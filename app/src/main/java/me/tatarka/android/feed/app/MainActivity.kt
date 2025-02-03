@file:OptIn(ExperimentalMaterial3Api::class)

package me.tatarka.android.feed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.tatarka.android.feed.app.ui.theme.FeedTheme

enum class Page(val route: String, val title: String) {
    LoadFromStart("/one", "Usecase: Load from start"),
    SaveScrollPosition("/two", "Usecase: Save scroll position"),
    LoadFromEnd("/three", "Usecase: Load from end"),
    PagingSourceOnly("/four", "Usecase: PagingSource only"),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FeedTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = "/") {
                    composable("/") {
                        Page(navController, Modifier.fillMaxSize())
                    }
                    composable(Page.LoadFromStart.route) {
                        LoadFromStatePage(Modifier.fillMaxSize())
                    }
                    composable(Page.SaveScrollPosition.route) {
                        SaveScrollPositionPage(Modifier.fillMaxSize())
                    }
                    composable(Page.LoadFromEnd.route) {
                        LoadFromEndPage(Modifier.fillMaxSize())
                    }
                    composable(Page.PagingSourceOnly.route) {
                        PagingSourceOnlyPage(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Feed") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            for (page in Page.entries) {
                ListItem(
                    headlineContent = { Text(page.title) },
                    modifier = Modifier.clickable {
                        navController.navigate(page.route)
                    }
                )
            }
        }
    }
}

class Item(
    val id: Long,
    val name: String,
    val checked: Boolean = false,
    val onCheckedChange: (Boolean) -> Unit = {},
) {
    override fun toString(): String {
        return "Item(id=$id, name='$name')"
    }
}

@Composable
fun MyListItem(name: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = {
            Text(name)
        },
        supportingContent = {
            Text("A list item")
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
fun PlaceholderListItem() {
    ListItem(
        headlineContent = {},
        supportingContent = {},
        trailingContent = {}
    )
}

@Preview(showSystemUi = true)
@Composable
private fun MainPagePreview() {
    Page(rememberNavController())
}