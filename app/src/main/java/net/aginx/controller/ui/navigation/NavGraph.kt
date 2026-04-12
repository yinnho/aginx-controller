package net.aginx.controller.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.aginx.controller.ui.MainViewModel
import net.aginx.controller.ui.home.HomeScreen
import net.aginx.controller.ui.add.AddAginxScreen
import net.aginx.controller.ui.agents.AgentListScreen
import net.aginx.controller.ui.agents.ConversationListScreen
import net.aginx.controller.ui.chat.ChatScreen

@Composable
fun NavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // Home - Aginx 列表
        composable("home") {
            HomeScreen(
                onAddAginx = { navController.navigate("add_aginx") },
                onSelectAginx = { aginxId ->
                    viewModel.connectAginxById(aginxId)
                    navController.navigate("agent_list/$aginxId")
                },
                viewModel = viewModel
            )
        }

        // AddAginx
        composable("add_aginx") {
            AddAginxScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        // AgentList
        composable(
            route = "agent_list/{aginxId}",
            arguments = listOf(navArgument("aginxId") { type = NavType.StringType })
        ) { backStackEntry ->
            val aginxId = backStackEntry.arguments?.getString("aginxId") ?: ""

            AgentListScreen(
                onBack = { navController.popBackStack() },
                onSelectAgent = { agentId ->
                    navController.navigate("conversation_list/$aginxId/$agentId")
                },
                viewModel = viewModel,
                aginxId = aginxId
            )
        }

        // ConversationList - 从服务端获取对话列表
        composable(
            route = "conversation_list/{aginxId}/{agentId}",
            arguments = listOf(
                navArgument("aginxId") { type = NavType.StringType },
                navArgument("agentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val aginxId = backStackEntry.arguments?.getString("aginxId") ?: ""
            val agentId = backStackEntry.arguments?.getString("agentId") ?: ""

            ConversationListScreen(
                aginxId = aginxId,
                agentId = agentId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSelectConversation = { serverSessionId ->
                    // 恢复已有对话，传递服务器 sessionId
                    navController.navigate("chat/$aginxId/$agentId/$serverSessionId")
                }
            )
        }

        // Chat - sessionId 可以是服务端 sessionId 或 "new"
        composable(
            route = "chat/{aginxId}/{agentId}/{sessionId}",
            arguments = listOf(
                navArgument("aginxId") { type = NavType.StringType },
                navArgument("agentId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val aginxId = backStackEntry.arguments?.getString("aginxId") ?: ""
            val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            ChatScreen(
                aginxId = aginxId,
                agentId = agentId,
                conversationId = sessionId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
