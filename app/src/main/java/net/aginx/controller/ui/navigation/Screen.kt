package net.aginx.controller.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddAginx : Screen("add_aginx")
    object AgentList : Screen("agent_list/{aginxId}") {
        fun createRoute(aginxId: String) = "agent_list/$aginxId"
    }
    object Chat : Screen("chat/{aginxId}/{agentId}/{conversationId}") {
        fun createRoute(aginxId: String, agentId: String, conversationId: String) =
            "chat/$aginxId/$agentId/$conversationId"
    }
}
