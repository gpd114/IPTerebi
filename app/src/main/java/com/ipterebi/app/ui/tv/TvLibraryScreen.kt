package com.ipterebi.app.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.app.ui.DpadTextField
import com.ipterebi.app.ui.fieldColours
import com.ipterebi.app.ui.library.LibraryViewModel
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.core.ListedItem
import com.ipterebi.core.cleanListName
import com.ipterebi.core.OwnList
import com.ipterebi.core.XtreamCategory
import java.util.Locale

/**
 * A library on a television: whatever the remote is on, described at the top,
 * and the posters under it.
 *
 * The phone's screen is a search box, a shelf of categories and a grid of
 * 112dp tiles, and it is the wrong shape here for two reasons. A tile that
 * size is a postage stamp across a room. And the name under it, clipped to two
 * lines, tells you nothing on a real line: the titles are
 * `4K-OSN+ - The Last of Us (2023) (US)`, where everything that identifies the
 * thing is at the end. So what the cursor is on gets the top of the screen at
 * a size that reads from a sofa, and the grid below is what moves.
 *
 * Everything that is not drawing is the phone's: the same [LibraryViewModel],
 * the same one category at a time, the same own lists, the same hold-to-add.
 */
@Composable
fun <T : Any> TvLibraryScreen(
    viewModel: LibraryViewModel<T>,
    key: (T) -> Any,
    nameOf: (T) -> String,
    imageOf: (T) -> String,
    ratingOf: (T) -> Double?,
    plotOf: (T) -> String,
    onOpen: (T) -> Unit,
    onOpenListed: (ListedItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // What the panel at the top is describing: what the cursor is on, not what
    // was chosen. On a television the thing under the cursor is the thing
    // being considered, and there is room to describe it.
    var focused by remember { mutableStateOf<T?>(null) }
    var focusedEntry by remember { mutableStateOf<ListedItem?>(null) }

    // Held: which poster, and which question. Putting something in a list is
    // asked of the panel's own grid; taking it out is asked inside a list.
    // A panel rather than a sheet, because a sheet from the bottom of a
    // television is a sheet nobody can reach with a remote.
    var adding by remember { mutableStateOf<T?>(null) }
    var removing by remember { mutableStateOf<ListedItem?>(null) }

    val shownList: OwnList? = state.selectedList?.let { name ->
        state.lists.firstOrNull { it.name == name }
    }

    // With nothing under the cursor yet, the panel says where you are rather
    // than standing empty: the category or list, and how much is in it.
    val here = shownList?.name
        ?: state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name
        ?: "All"
    val count = shownList?.items?.size ?: state.visible.size
    val nothingFocused = focused == null && focusedEntry == null

    Column(Modifier.fillMaxSize().background(TvGround)) {
        Detail(
            title = focusedEntry?.name ?: focused?.let(nameOf) ?: here,
            poster = focusedEntry?.poster ?: focused?.let(imageOf).orEmpty(),
            // A list entry carries a name and a poster and nothing else: it
            // was stored to be drawn, not described.
            rating = if (focusedEntry != null) null else focused?.let(ratingOf),
            plot = when {
                nothingFocused -> if (count == 1) "1 here" else count.toString() + " here"
                focusedEntry != null -> ""
                else -> focused?.let(plotOf).orEmpty()
            },
            footer = if (shownList != null) {
                "OK  Watch      Hold OK  Take out of this list"
            } else {
                "OK  Watch      Hold OK  Put in a list"
            },
        )

        Categories(
            categories = state.categories,
            lists = state.lists,
            selectedCategoryId = state.selectedCategoryId,
            selectedList = state.selectedList,
            onCategory = viewModel::selectCategory,
            onList = viewModel::selectList,
        )

        when {
            // A list of the viewer's own: already on the device, so nothing is
            // fetched and nothing can fail.
            shownList != null -> PosterGrid(
                items = shownList.items,
                key = { it.kind.name + ":" + it.id },
                nameOf = { it.name },
                imageOf = { it.poster },
                onFocus = { focusedEntry = it; focused = null },
                onOpen = onOpenListed,
                onHold = { removing = it },
            )

            state.busy && state.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TvAccent)
                }

            state.error != null ->
                Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = TvInkSoft,
                    )
                }

            state.visible.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing in this category.",
                        style = MaterialTheme.typography.titleMedium,
                        color = TvInkSoft,
                    )
                }

            else -> PosterGrid(
                items = state.visible,
                key = key,
                nameOf = nameOf,
                imageOf = imageOf,
                onFocus = { focused = it; focusedEntry = null },
                onOpen = onOpen,
                onHold = { adding = it },
            )
        }
    }

    // Over the grid, taking the remote while it is up — the same bargain the
    // guide's groups panel makes. Back closes it.
    adding?.let { held ->
        val entry = viewModel.listedForm(held)
        ListPanel(
            title = entry.name,
            lists = state.allLists.map { it.name to it.holds(entry.kind, entry.id) },
            onChoose = { name, alreadyIn ->
                if (alreadyIn) viewModel.removeFromList(name, entry.id)
                else viewModel.addToList(name, held)
                adding = null
            },
            onNew = { name ->
                viewModel.addToList(name, held)
                adding = null
            },
            onClose = { adding = null },
        )
    }

    removing?.let { entry ->
        val from = state.selectedList
        ListPanel(
            title = entry.name,
            lists = if (from == null) emptyList() else listOf(from to true),
            onChoose = { name, _ ->
                viewModel.removeFromList(name, entry.id)
                removing = null
            },
            onNew = null,
            onClose = { removing = null },
        )
    }
}

/**
 * The top of the screen: the poster large, the name at a size that reads
 * across a room, and whatever the panel said about it.
 *
 * Two lines for the name rather than one, because on a real line the part
 * that identifies a thing is at the end of it.
 */
@Composable
private fun Detail(
    title: String,
    poster: String,
    rating: Double?,
    plot: String,
    footer: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DetailHeight)
            .padding(start = 48.dp, top = 18.dp, end = 48.dp, bottom = 6.dp),
    ) {
        if (poster.isNotBlank()) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(Corners.card)
                    .background(CellFill),
            )
            Spacer(Modifier.width(24.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = TvInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            rating?.let {
                Text(
                    "★ " + String.format(Locale.ROOT, "%.1f", it),
                    style = MaterialTheme.typography.titleSmall,
                    color = TvAccent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (plot.isNotBlank()) {
                Text(
                    plot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvInkSoft,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(footer, style = MaterialTheme.typography.labelMedium, color = TvInkSoft)
        }
    }
}

/**
 * Categories and the viewer's own lists, one row that scrolls sideways.
 *
 * The phone's shelf wraps onto four rows, which is right for a thumb and
 * eats a television — see the D-pad note in CLAUDE.md. Here there is only
 * ever one row, so down from a chip lands in the posters.
 */
@Composable
private fun Categories(
    categories: List<XtreamCategory>,
    lists: List<OwnList>,
    selectedCategoryId: String?,
    selectedList: String?,
    onCategory: (String?) -> Unit,
    onList: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusGroup().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "all") {
            Chip(
                label = "All",
                selected = selectedCategoryId == null && selectedList == null,
                onClick = { onCategory(null) },
            )
        }
        // The viewer's own first and in pink, as Favourites is: a handful of
        // their things before dozens of the provider's.
        items(lists, key = { "l:" + it.name }) { own ->
            Chip(
                label = own.name,
                selected = selectedList == own.name,
                onClick = { onList(own.name) },
                mine = true,
            )
        }
        items(categories, key = { "c:" + it.id }) { category ->
            Chip(
                label = category.name,
                selected = selectedCategoryId == category.id,
                onClick = { onCategory(category.id) },
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, mine: Boolean = false) {
    TvRow(onClick = onClick, radius = 10.dp) { focusedHere ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                focusedHere -> TvFocusInk
                selected -> TvOnAccent
                mine -> TvPink
                else -> TvInkSoft
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                // Chosen is the accent, focus is cobalt: see TvParts. A list of
                // the viewer's own keeps its pink when it is the chosen one, so
                // the two kinds of chip stay apart.
                //
                // Transparent while focused, because this background would
                // otherwise paint over the focus fill the row puts behind it
                // and the chip the remote is on would look like any other.
                .background(
                    when {
                        focusedHere -> Color.Transparent
                        !selected -> CellFill
                        mine -> TvPink
                        else -> TvAccent
                    }
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/**
 * Posters big enough to read the name under, small enough to see a library:
 * six across a 1080p screen rather than the phone's eight.
 *
 * It was four, which is what 170dp works out to on a 960dp-wide television
 * once the rail is taken off, and the owner's verdict was that four is too
 * big. They are right about what a poster is for: at that size one row fills
 * the screen under the description strip, so the grid showed four films and
 * no sense that there were more. At six a full row and the top of the next
 * one fit, which is what says "keep going down".
 */
@Composable
private fun <I : Any> PosterGrid(
    items: List<I>,
    key: (I) -> Any,
    nameOf: (I) -> String,
    imageOf: (I) -> String,
    onFocus: (I) -> Unit,
    onOpen: (I) -> Unit,
    onHold: (I) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        items(items, key = key) { item ->
            TvRow(
                onClick = { onOpen(item) },
                onLongClick = { onHold(item) },
                onFocusChange = { if (it) onFocus(item) },
                radius = 12.dp,
            ) { focusedHere ->
                Column(Modifier.padding(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(Corners.card)
                            .background(CellFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        val image = imageOf(item)
                        if (image.isNotBlank()) {
                            // Fitted, never cropped: a provider uploads
                            // whatever shape it has. See the poster note.
                            AsyncImage(
                                model = image,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        nameOf(item),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (focusedHere) TvFocusInk else TvInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The panel a hold opens: which list, or a new one.
 *
 * Over the grid rather than up from the bottom — a sheet from the foot of a
 * television is a sheet a remote has to walk the length of the screen to
 * reach. It takes focus while it is up and gives it back on Back, which is
 * what the guide's groups panel does and so is what the remote expects.
 *
 * [lists] is each list with whether this thing is already in it, so one row
 * can say both "put it here" and "take it out of here".
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ListPanel(
    title: String,
    lists: List<Pair<String, Boolean>>,
    onChoose: (name: String, alreadyIn: Boolean) -> Unit,
    /** Null where making a list makes no sense: taking something out of one. */
    onNew: ((String) -> Unit)?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val entry = remember { FocusRequester() }

    // The release that ends the hold has not arrived yet.
    //
    // tv-material fires its long click while the key is still down, and it
    // acts on a centre key's release without caring whether it saw the press.
    // So the release that ends the hold lands on this panel's first row and
    // chooses it, and with a list already made that put the thing straight in
    // and closed again. With no lists it looked fine, because a text field is
    // not a button.
    //
    // A timer cannot catch it. How long a key is held is the viewer's choice,
    // and a 350 ms guard here missed a hold of about a second on the owner's
    // box: by the time they let go the panel had long since settled. What is
    // certain is the shape of the event, so the panel ignores centre keys
    // altogether until it sees a press of its own — a key down whose repeat
    // count is zero — and from then on behaves normally.
    //
    // The repeat count is the part that matters on the box. Its OK key does
    // repeat, so a hold is a down, a stream of repeats and then an up, and
    // the repeats land on this panel once it is open. Arming on any key down
    // would arm on those, and the release that followed would choose the row
    // under them. Only a press that starts from nothing counts.
    //
    // The guide learned the same lesson about the release that ends a hold
    // also opening the channel list; see `okHeld` there.
    var armed by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key !in Select) return@onPreviewKeyEvent false
                val fresh = event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0
                if (fresh) armed = true
                val swallowed = !armed
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG_PLAY,
                        "list panel key " + event.key + " " + event.type +
                            " repeat=" + event.nativeKeyEvent.repeatCount +
                            " armed=" + armed + " swallowed=" + swallowed,
                    )
                }
                swallowed
            }
            .background(Color(0xCC000000))
            .padding(horizontal = 120.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Focus stays in here while the panel is up.
                //
                // Down from the first row was landing on a poster behind the
                // panel, and from there nothing moved: the grid is still
                // composed underneath, the poster below the row starts a few
                // pixels nearer than the name field does, and Compose scores
                // by distance. Cancelling the exit restricts the search to
                // this group, so down goes row, field, button, which is the
                // order they are read in.
                //
                // Deactivating the grid instead does not work: focusGroup is
                // itself `canFocus = false`, and a deactivated group is
                // exactly one whose children are still reachable.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .clip(Corners.panel)
                .background(TvPanel)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = TvInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (lists.isEmpty() && onNew == null) {
                Text("Not in a list.", style = MaterialTheme.typography.bodyMedium, color = TvInkSoft)
            }
            lists.forEachIndexed { index, (name, alreadyIn) ->
                TvRow(
                    onClick = { onChoose(name, alreadyIn) },
                    radius = 10.dp,
                    modifier = if (index == 0) Modifier.focusRequester(entry) else Modifier,
                ) { focusedHere ->
                    Text(
                        if (alreadyIn) "$name   ·   take out" else name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (focusedHere) TvFocusInk else TvInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
            if (onNew != null) {
                // Click-to-edit, as every field in this app is under a remote:
                // a field that takes focus on the way past traps the arrows
                // and throws a keyboard over the screen.
                DpadTextField(
                    modifier = if (lists.isEmpty()) Modifier.focusRequester(entry) else Modifier,
                ) { fieldModifier ->
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New list") },
                        singleLine = true,
                        shape = Corners.control,
                        colors = fieldColours(),
                        modifier = fieldModifier.fillMaxWidth(),
                    )
                }
                TvButton(
                    text = "Make the list and add",
                    onClick = { cleanListName(newName)?.let(onNew) },
                )
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { entry.requestFocus() } }
}

/**
 * How much of the screen the description takes.
 *
 * Shorter than the guide's top strip, which is 213dp: that one has a
 * programme, its times and a player in the corner to fit, and this has a
 * poster and a name. On a 540dp television the difference is a whole row of
 * covers — at the guide's height the first row was cut off by the bottom
 * edge, which is the same mistake the wrapped category shelf made.
 */
private val DetailHeight = 168.dp

/**
 * How long after a panel opens its own buttons stay deaf.
 *
 * Long enough to swallow the release of the hold that opened it, short
 * enough that nobody deliberately pressing OK notices: a hold is 400 ms and
 * the release lands a frame or two after that.
 */
private val Select = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
