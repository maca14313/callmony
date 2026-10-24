package com.callmony.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.maskAccount
import com.callmony.lib.relTime
import com.callmony.model.BankAccount
import com.callmony.model.Note
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.SwipeAction
import com.callmony.ui.components.SwipeRow
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Card
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.EmptyState
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.Toasts

/* NotesSection — pinned-first note cards with tags, colors, swipe actions.
   Port of src/sections/NotesSection.tsx. */

val NOTE_COLORS = listOf("#E4EDE7", "#F7E8DF", "#E5E8F9", "#F4E5EE", "#F4EEDB")

@Composable
fun NotesSection(ownerId: String) {
    val C = C()
    val notes = AppStore.state.notes

    val mine = remember(notes, ownerId) {
        notes.filter { it.ownerId == ownerId && it.deletedAt == null }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }

    if (mine.isEmpty()) {
        EmptyState(
            icon = "notebook-outline",
            title = t("nt.noNotes"),
            hint = if (ownerId == "general") t("nt.generalNotesHint") else t("nt.notesHint"),
            actionLabel = t("nt.newNote"),
            onAction = { UiStore.open(Overlay.NoteEdit(null, ownerId)) },
        )
        return
    }

    Column {
        Row(Modifier.padding(bottom = 10.dp)) {
            Btn(small = true, label = t("nt.newNote"), icon = "plus", onPress = { UiStore.open(Overlay.NoteEdit(null, ownerId)) })
        }
        mine.forEach { n ->
            Column(Modifier.padding(bottom = 8.dp)) {
                SwipeRow(
                    key = n.id,
                    left = listOf(SwipeAction(t("nt.pinned"), if (n.pinned) "pin-off-outline" else "pin", Color(0xFFb97d10)) {
                        AppStore.saveNote(n.copy(pinned = !n.pinned, updatedAt = com.callmony.lib.now()))
                    }),
                    right = listOf(
                        SwipeAction(t("act.edit"), "pencil-outline", Color(0xFF4a7d9b)) {
                            UiStore.open(Overlay.NoteEdit(n.id, ownerId))
                        },
                        SwipeAction(t("act.delete"), "trash-can-outline", C.red) {
                            Confirm.ask(t("nt.deleteTitle"), t("nt.deleteMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteNote(n.id); Toasts.toastUndo() }
                            }
                        },
                    ),
                    onTap = { UiStore.open(Overlay.NoteEdit(n.id, ownerId)) },
                ) {
                    Card {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            n.color?.let {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(parseNoteColor(it))
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        n.title.ifEmpty { t("nt.untitled") },
                                        fontSize = F.body.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = C.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (n.pinned) MdiIcon("pin", 14, Color(0xFFb97d10))
                                }
                                if (n.body.isNotEmpty()) {
                                    Text(n.body, fontSize = F.small.sp, color = C.sub, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    n.tags.take(4).forEach { tag ->
                                        Text("#$tag", fontSize = F.tiny.sp, color = C.brand, fontWeight = FontWeight.Bold)
                                    }
                                    if (n.tags.size > 4) {
                                        Text("+${n.tags.size - 4}", fontSize = F.tiny.sp, color = C.faint)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(relTime(n.updatedAt), fontSize = F.tiny.sp, color = C.faint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseNoteColor(hex: String): Color = com.callmony.ui.kit.parseHexColor(hex)

/* BankSection — account cards with masked numbers, reveal/copy/share.
   Port of src/sections/BankSection.tsx. */

@Composable
fun BankSection(ownerId: String) {
    val C = C()
    val banks = AppStore.state.banks
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var shareFor by remember { mutableStateOf<BankAccount?>(null) }

    val mine = remember(banks, ownerId) {
        banks.filter { it.ownerId == ownerId && it.deletedAt == null }.sortedByDescending { it.updatedAt }
    }
    val totalBalance = mine.sumOf { it.balance ?: 0.0 }

    if (mine.isEmpty()) {
        EmptyState(
            icon = "bank-outline",
            title = t("bk.noAccounts"),
            hint = t("bk.noAccountsHint"),
            actionLabel = t("bk.addAccount"),
            onAction = { UiStore.open(Overlay.BankEdit(null, ownerId)) },
        )
        return
    }

    fun toggleReveal(id: String) {
        revealed = if (revealed.contains(id)) revealed - id else revealed + id
    }

    Column {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(small = true, label = t("bk.addAccount"), icon = "plus", onPress = { UiStore.open(Overlay.BankEdit(null, ownerId)) })
            if (totalBalance != 0.0) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Money(totalBalance, size = 16, weight = FontWeight.ExtraBold, compact = true)
                }
            }
        }
        mine.forEach { b ->
            Column(Modifier.padding(bottom = 8.dp)) {
                SwipeRow(
                    key = b.id,
                    left = listOf(SwipeAction(t("bk.showNumber"), "eye-outline", Color(0xFF4a7d9b)) { toggleReveal(b.id) }),
                    right = listOf(
                        SwipeAction(t("act.share"), "share-variant-outline", C.brand) { shareFor = b },
                        SwipeAction(t("act.delete"), "trash-can-outline", C.red) {
                            Confirm.ask(t("bk.deleteTitle"), t("bk.deleteMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteBank(b.id); Toasts.toastUndo() }
                            }
                        },
                    ),
                    onTap = { UiStore.open(Overlay.BankEdit(b.id, ownerId)) },
                ) {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(C.brandSoft),
                                contentAlignment = Alignment.Center,
                            ) { MdiIcon("bank", 19, C.brand) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(b.bankName ?: b.label, fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(b.holderName ?: t("bk.holderOptional"), fontSize = F.tiny.sp, color = C.sub, maxLines = 1)
                            }
                            b.balance?.let { Money(it, size = 15, compact = true) }
                            IconBtn(if (revealed.contains(b.id)) "eye-off-outline" else "eye-outline", { toggleReveal(b.id) }, size = 34)
                        }
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (revealed.contains(b.id)) (b.accountNumber ?: "") else maskAccount(b.accountNumber),
                                fontSize = F.small.sp,
                                color = C.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!b.accountNumber.isNullOrEmpty()) {
                                IconBtn("content-copy", {
                                    Platform.share.copyText(b.accountNumber!!)
                                    Toasts.toast(t("bk.detailsCopied"))
                                }, size = 30)
                            }
                        }
                    }
                }
            }
        }
    }

    // share sheet
    shareFor?.let { b ->
        BottomSheet(title = t("bk.shareAsText"), onClose = { shareFor = null }) {
            Column {
                Card(Modifier.padding(bottom = 12.dp)) {
                    Text(
                        buildString {
                            append(b.bankName ?: b.label)
                            append("\n")
                            b.holderName?.let { append("${t("bk.holder")}: $it\n") }
                            append("${t("bk.accountNumber")}: ${b.accountNumber ?: t("bk.noNumber")}")
                            b.balance?.let { append("\n${t("lg.balance")}: $it") }
                        },
                        fontSize = F.body.sp,
                        color = C.ink,
                        lineHeight = 24.sp,
                    )
                }
                Btn(label = t("act.share"), icon = "share-variant-outline", onPress = {
                    Platform.share.shareText("${b.bankName ?: b.label} · ${b.accountNumber ?: ""}", b.bankName ?: b.label)
                    Toasts.toast(t("bk.shared"))
                    shareFor = null
                })
            }
        }
    }
}
