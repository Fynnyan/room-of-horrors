package components

import com.fynnian.application.common.I18n
import csstype.TextAlign
import js.core.jso
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.dom.onChange
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement
import web.html.InputType

external interface RoomGroupInfoProps : Props {
  var text: String?
  var onGroupInfoSubmit: (name: String, size: Int) -> Unit
}

val RoomGroupInfo = FC<RoomGroupInfoProps> { props ->
  val (language) = useContext(LanguageContext)
  val (groupSize, setGroupSize) = useState<Int?>(null)
  val (groupName, setGroupName) = useState<String?>(null)

  Card {
    CardHeader {
      sx {
        textAlign = TextAlign.center
      }
      title = ReactNode(I18n.get(language, I18n.TranslationKey.ROOM_GROUP_INFO_TITLE))
      titleTypographyProps = jso {
        variant = TypographyVariant.body1
      }
    }
    Spacer { size = SpacerPropsSize.SMALL }
    CardContent {
      sx {
        textAlign = TextAlign.center
      }
      if (props.text != null) Typography {
        variant = TypographyVariant.body1
        +props.text!!
      }

      FormGroup {
        TextField {
          id = "groupName"
          name = "groupName"
          required = true
          type = InputType.text
          label = ReactNode(I18n.get(language, I18n.TranslationKey.ROOM_GROUP_INFO_GROUP_NAME_LABEL))
          placeholder = "name"
          value = groupName
          onChange = { setGroupName(it.target.unsafeCast<HTMLTextAreaElement>().value.ifBlank { null }) }
        }
      }
      Spacer { size = SpacerPropsSize.SMALL }
      FormGroup {
        TextField {
          id = "groupSize"
          name = "groupSize"
          required = true
          type = InputType.number
          label = ReactNode(I18n.get(language, I18n.TranslationKey.ROOM_GROUP_INFO_GROUP_SIZE_LABEL))
          placeholder = "2"
          value = groupSize ?: ""
          onChange = { setGroupSize(it.target.unsafeCast<HTMLInputElement>().value.toIntOrNull()) }
        }
      }
      Spacer { size = SpacerPropsSize.SMALL }
      Button {
        disabled = groupName.isNullOrBlank() || groupSize == null
        onClick = {
          props.onGroupInfoSubmit(groupName!!, groupSize!!)
        }
        +I18n.get(language, I18n.TranslationKey.ROOM_GROUP_INFO_GROUP_BUTTON)
      }
    }
  }
}