package net.aginx.controller.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import net.aginx.controller.ui.MainViewModel
import net.aginx.controller.ui.home.HomeScreen
import net.aginx.controller.ui.add.AddAginxScreen
import net.aginx.controller.ui.agents.AgentListScreen
import net.aginx.controller.ui.chat.ChatScreen

@Composable
fun NavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

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
                    scope.launch {
                        val count = viewModel.getConversationCount(aginxId, agentId)
                        if (count == 0) {
                            val conv = viewModel.createConversation(aginxId, agentId, null)
                            navController.navigate("chat/$aginxId/$agentId/${conv.id}")
                        } else {
                            navController.navigate("chat/$aginxId/$agentId/latest")
                        }
                    }
                },
                viewModel = viewModel,
                aginxId = aginxId
            )
        }

        // Chat
        composable(
            route = "chat/{aginxId}/{agentId}/{conversationId}",
            arguments = listOf(
                navArgument("aginxId") { type = NavType.StringType },
                navArgument("agentId") { type = NavType.StringType },
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val aginxId = backStackEntry.arguments?.getString("aginxId") ?: ""
            val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""

            ChatScreen(
                aginxId = aginxId,
                agentId = agentId,
                conversationId = conversationId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNewConversation = {
                    scope.launch {
                        val conv = viewModel.createConversation(aginxId, agentId, null)
                        navController.navigate("chat/$aginxId/$agentId/${conv.id}")
                    }
                }
            )
        }
    }
}
