package me.tatarka.android.feed.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import me.tatarka.android.feed.app.api.ItemApi
import me.tatarka.android.feed.app.db.AppDatabase
import me.tatarka.android.feed.app.ui.theme.FeedTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scope = rememberCoroutineScope()
            val repo = remember {
                ItemRepository(
                    api = ItemApi(),
                    db = AppDatabase.get(this),
                    scope = scope,
                    prefs = { getSharedPreferences("prefs", Context.MODE_PRIVATE) },
                )
            }
            FeedTheme {
                MyPage(
                    list = repo.items.collectAsLazyList(
                        onUpdateScrollPosition = FeedListScrollPositions.firstVisibleIndex {
                            repo.updateScrollPosition(it.id)
                        },
                    )
                )
            }
        }
    }
}

@Composable
fun MyPage(list: FeedLazyList<Item>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { scope.launch { list.refresh() } }) {
                Text("Refresh")
            }
        }
    ) { innerPadding ->
        MyList(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            list = list
        )
    }
}

@Composable
fun MyList(list: FeedLazyList<Item>, modifier: Modifier = Modifier) {
    list.listState?.let { listState ->
        LazyColumn(modifier = modifier, state = listState) {
            items(list, key = { it.item.id }) { entry ->
                val item = entry.item
                MyListItem(
                    name = item.name,
                    checked = item.checked,
                    onCheckedChange = item.onCheckedChange,
                )
            }
        }
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

@Preview(showBackground = true)
@Composable
fun MyPagePreview() {
    FeedTheme {
        MyPage(
            modifier = Modifier.fillMaxWidth(),
            list = feedLazyListOf(Item(id = 0, "1"), Item(id = 1, "2"))
        )
    }
}