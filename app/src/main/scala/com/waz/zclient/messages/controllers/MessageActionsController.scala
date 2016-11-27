package com.waz.zclient.messages.controllers

import android.app.{Activity, ProgressDialog}
import android.content.DialogInterface.OnDismissListener
import android.content.{ClipData, ClipboardManager, DialogInterface}
import android.net.Uri
import android.support.v4.app.ShareCompat
import android.support.v7.app.AlertDialog
import android.widget.Toast
import com.waz.api.{Asset, ImageAsset, Message}
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.utils._
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.common.controllers.{PermissionsController, WriteExternalStoragePermission}
import com.waz.zclient.controllers.global.KeyboardController
import com.waz.zclient.controllers.tracking.ITrackingController
import com.waz.zclient.controllers.tracking.events.connect.OpenedGenericInviteMenuEvent.EventContext
import com.waz.zclient.controllers.tracking.events.conversation._
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.core.controllers.tracking.events.filetransfer.SavedFileEvent
import com.waz.zclient.notifications.controllers.ImageNotificationsController
import com.waz.zclient.pages.main.conversation.views.MessageBottomSheetDialog
import com.waz.zclient.pages.main.conversation.views.MessageBottomSheetDialog.{Callback, MessageAction}
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}

// TODO: rewrite to not use java message api
// TODO: don't call tracking controller directly, expose generic event streams instead
class MessageActionsController(implicit injector: Injector, ec: EventContext) extends Injectable {
  import com.waz.threading.Threading.Implicits.Ui

  private val context                   = inject[Activity]
  private lazy val keyboardController   = inject[KeyboardController]
  private lazy val trackingController   = inject[ITrackingController]
  private lazy val userPrefsController  = inject[IUserPreferencesController]
  private lazy val clipboardManager     = inject[ClipboardManager]
  private lazy val permissions          = inject[PermissionsController]
  private lazy val imageNotifications   = inject[ImageNotificationsController]

  private val zms = inject[Signal[ZMessaging]]

  val onMessageAction = EventStream[(MessageAction, Message)]()

  private var dialog = Option.empty[MessageBottomSheetDialog]

  private val callback = new Callback {
    override def onAction(action: MessageAction, message: Message, messageViewController: MessageViewController): Unit = {
      onMessageAction ! (action, message)
    }
  }

  // TODO: this should be done inside of tracking controller
  onMessageAction {
    case (action, message) =>
      trackingController.tagEvent(OpenedMessageActionEvent.forAction(action, message.getMessageType.name))
  }

  onMessageAction {
    case (MessageAction.COPY, message)          => copyMessage(message)
    case (MessageAction.DELETE_GLOBAL, message) => recallMessage(message)
    case (MessageAction.DELETE_LOCAL, message)  => deleteMessage(message)
    case (MessageAction.FORWARD, message)       => forwardMessage(message)
    case (MessageAction.LIKE, message)          => toggleLike(message)
    case (MessageAction.UNLIKE, message)        => toggleLike(message)
    case (MessageAction.SAVE, message)          => saveMessage(message)
    //  case (MessageAction.OPEN_FILE, message) => // TODO: call start download on message view
    //            if (message.getMessageType() == Message.Type.ANY_ASSET).startAssetDownLoad();
    case _ => // should be handled somewhere else
  }

  private val onDismissed = new OnDismissListener {
    override def onDismiss(dialogInterface: DialogInterface): Unit = dialog = None
  }

  private def isConvMember(conv: ConvId) = zms.head.flatMap { zs =>
    zs.membersStorage.isActiveMember(conv, zs.selfUserId)
  }

  def showDialog(data: MessageAndLikes): Boolean = {
    val msg = data.message
    if (msg.isEphemeral) return false

    for {
      isMember <- isConvMember(msg.convId)
      _ <- keyboardController.hideKeyboardIfVisible()   // TODO: keyboard should be handled in more generic way
      message = ZMessaging.currentUi.messages.cachedOrUpdated(data)
    } yield {
      dialog.foreach(_.dismiss())
      dialog = Some(
        returning(new MessageBottomSheetDialog(context, R.style.message__bottom_sheet__base, message, null, isMember, callback)) { d =>
          d.setOnDismissListener(onDismissed)
          d.show()
        }
      )
    }
    true
  }

  private def toggleLike(message: Message) = {
    if (message.isLikedByThisUser) {
      message.unlike()
      trackingController.tagEvent(ReactedToMessageEvent.unlike(message.getConversation, message, ReactedToMessageEvent.Method.MENU))
    }
    else {
      message.like()
      userPrefsController.setPerformedAction(IUserPreferencesController.LIKED_MESSAGE)
      trackingController.tagEvent(ReactedToMessageEvent.like(message.getConversation, message, ReactedToMessageEvent.Method.MENU))
    }
  }

  private def copyMessage(message: Message) = {
    trackingController.tagEvent(new CopiedMessageEvent(message.getMessageType.name))
    val clip = ClipData.newPlainText(getString(R.string.conversation__action_mode__copy__description, message.getUser.getDisplayName), message.getBody)
    clipboardManager.setPrimaryClip(clip)
    Toast.makeText(context, R.string.conversation__action_mode__copy__toast, Toast.LENGTH_SHORT).show()
  }

  private def editMessage(message: Message) = {
//    TODO: handle that in some better place, maybe cursor view?
//    if (cursorLayout == null) return
//    cursorLayout.editMessage(message)
//    getControllerFactory.getConversationScreenController.setMessageBeingEdited(message)
//    // Add small delay so triggering keyboard works
//    new Handler().postDelayed(new Runnable() {
//      def run() {
//        KeyboardUtils.showKeyboard(getActivity)
//      }
//    }, 200)
  }

  private def deleteMessage(message: Message) =
    showDeleteDialog(R.string.conversation__message_action__delete_for_me) {
      message.delete()
      trackingController.tagEvent(new DeletedMessageEvent(message, false))
    }

  private def recallMessage(message: Message) =
    showDeleteDialog(R.string.conversation__message_action__delete_for_everyone) {
      message.recall()
      trackingController.tagEvent(new DeletedMessageEvent(message, true))
    }

  private def showDeleteDialog(title: Int)(onSuccess: => Unit) =
    new AlertDialog.Builder(context)
      .setTitle(title)
      .setMessage(R.string.conversation__message_action__delete_details)
      .setCancelable(true)
      .setNegativeButton(R.string.conversation__message_action__delete__dialog__cancel, null)
      .setPositiveButton(R.string.conversation__message_action__delete__dialog__ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          onSuccess
          Toast.makeText(context, R.string.conversation__message_action__delete__confirmation, Toast.LENGTH_SHORT).show()
        }
      })
      .create()
      .show()

  private def forwardMessage(message: Message) = {
    trackingController.tagEvent(new ForwardedMessageEvent(message.getMessageType.name))
    val asset = message.getAsset
    val intentBuilder = ShareCompat.IntentBuilder.from(context)
    intentBuilder.setChooserTitle(R.string.conversation__action_mode__fwd__chooser__title)
    if (asset.isEmpty) { // TODO: handle location and other non text messages
      intentBuilder.setType("text/plain")
      intentBuilder.setText(message.getBody)
      intentBuilder.startChooser()
    } else {
      val dialog = ProgressDialog.show(context,
        getString(R.string.conversation__action_mode__fwd__dialog__title),
        getString(R.string.conversation__action_mode__fwd__dialog__message), true, true, null)

      asset.getContentUri(new Asset.LoadCallback[Uri]() {
        def onLoaded(uri: Uri): Unit = {
          dialog.dismiss()
          intentBuilder.setType(asset.getMimeType)
          intentBuilder.addStream(uri)
          intentBuilder.startChooser()
        }
        def onLoadFailed(): Unit = {
          // TODO: show error info
          dialog.dismiss()
        }
      })
    }
  }

  private def saveMessage(message: Message) =
    permissions.requiring(WriteExternalStoragePermission) {  // TODO: provide explanation dialog - use requiring with message str
      if (message.getMessageType == Message.Type.ASSET) { // TODO: simplify once SE asset v3 is merged, we should be able to handle that without special conditions
        val asset = message.getImage
        asset.saveImageToGallery(new ImageAsset.SaveCallback() {
          def imageSaved(uri: Uri): Unit = {
            imageNotifications.showImageSavedNotification(asset.getId, uri)
            Toast.makeText(context, R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show()
          }
          def imageSavingFailed(ex: Exception): Unit =
            Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
        })
      } else {
        val dialog = ProgressDialog.show(context, getString(R.string.conversation__action_mode__fwd__dialog__title), getString(R.string.conversation__action_mode__fwd__dialog__message), true, true, null)
        val asset = message.getAsset
        asset.saveToDownloads(new Asset.LoadCallback[Uri]() {
          def onLoaded(uri: Uri) {
            trackingController.tagEvent(new SavedFileEvent(asset.getMimeType, asset.getSizeInBytes.toInt))
            Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_completed, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
          }

          def onLoadFailed() {
            Toast.makeText(context, com.waz.zclient.ui.R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
          }
        })
      }
    }
}
