package components

import com.fynnian.application.common.room.Room
import csstype.rem
import mui.material.Box
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.FC
import react.Props

external interface RoomInfoProps : Props {
  var room: Room
}

val RoomInfo = FC<RoomInfoProps> { props ->

  Box {
    sx {
      padding = 0.5.rem
    }
    Typography {
      variant = TypographyVariant.body1
      +"Room name ${props.room.title}"
    }
    Typography {
      variant = TypographyVariant.body1
      +"Room Code: ${props.room.code}"
    }
    Typography {
      variant = TypographyVariant.body1
      +props.room.description
    }
    Typography {
      variant = TypographyVariant.body1
      + props.room.question
    }
  }
}