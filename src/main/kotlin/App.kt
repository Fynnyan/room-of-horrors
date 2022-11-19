import browser.document
import components.ThemeModule
import mui.material.useMediaQuery
import pages.Landingpage
import pages.Management
import pages.RoomPage
import react.FC
import react.Props
import react.create
import react.createElement
import react.dom.client.createRoot
import react.router.Route
import react.router.Routes
import react.router.dom.BrowserRouter

fun main() {
  createRoot(
    document.createElement("div").also {
      document.body.appendChild(it)
    }
  ).render(App.create())
}

private val App = FC<Props> {
  val mobileMode = useMediaQuery("(max-width:960px)")
  ThemeModule {
    Routing()
  }
}

private val Routing = FC<Props> {
  BrowserRouter {
    Routes {
      Route {
        index = true
        element = createElement(Landingpage)
      }
      Route {
        path = AppPaths.ROOM.path + ":id"
        element = createElement(RoomPage)
      }
      Route {
        path = AppPaths.MANAGEMENT.path
        element = createElement(Management)
      }
    }
  }
}

enum class AppPaths(val path: String) {
  HOME("/"),
  ROOM("/room/"),
  MANAGEMENT("/management")
}