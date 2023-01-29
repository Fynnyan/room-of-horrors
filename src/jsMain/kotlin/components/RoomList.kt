package components

import api.RoomManagementApi
import com.fynnian.application.common.AppPaths
import com.fynnian.application.common.room.RoomDetails
import csstype.FlexDirection
import csstype.rem
import mui.icons.material.FileDownload
import mui.icons.material.Launch
import mui.material.IconButton
import mui.material.Link
import mui.material.ListItem
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.FC
import react.Props
import react.router.useNavigate
import web.dom.document
import web.html.HTML

external interface RoomListProps : Props {
  var rooms: List<RoomDetails>
}

val RoomList = FC<RoomListProps> {props ->
  mui.material.List {
    sx {

    }
    props.rooms.map {
      RoomListItem {
        room = it
      }
    }
  }
}

external interface RoomListItemProp : Props {
  var room: RoomDetails
}

val RoomListItem = FC<RoomListItemProp> { props ->
  val room = props.room
  val navigate = useNavigate()

  ListItem {
    sx {
      flexDirection = FlexDirection.row
      gap = 1.rem
    }
    Typography {
      variant = TypographyVariant.body1
      + room.code
    }
    Typography {
      variant = TypographyVariant.body1
      + room.title
    }
    Typography {
      variant = TypographyVariant.body1
      + "participants: ${room.participants}"
    }
    Typography {
      variant = TypographyVariant.body1
      + "total answers: ${room.answers}"
    }
    IconButton {
      onClick = { navigate(AppPaths.ROOM.path + "/${room.code}")}
      Launch { }
    }
    RoomQRCodeDialog {
      roomCode = room.code
    }
    IconButton {
      onClick = {
        document.createElement(HTML.a)
          .apply {
            href = RoomManagementApi.roomExportUrl(room.code)
            download = "room-export-${room.code}.xlsx"
          }
          .also {
            document.body.appendChild(it)
            it.click()
            document.body.removeChild(it)
          }
      }
      FileDownload()
    }
  }
}