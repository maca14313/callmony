/* NotesSection — pinned-first note cards with tags, colors, swipe actions. */

import React, { useMemo } from "react";
import { StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { relTime } from "../lib/utils";
import type { Note, OwnerId } from "../lib/types";
import { F, useThemeColors } from "../theme";
import { Btn, Card, EmptyState, confirm, toast } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";

const NOTE_COLORS = ["#E4EDE7", "#F7E8DF", "#E5E8F9", "#F4E5EE", "#F4EEDB"];

export const NotesSection: React.FC<{ ownerId: OwnerId }> = ({ ownerId }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const notes = useApp((s) => s.notes);
  const deleteNote = useApp((s) => s.deleteNote);
  const saveNote = useApp((s) => s.saveNote);

  const mine = useMemo(
    () =>
      notes
        .filter((n) => n.ownerId === ownerId && !n.deletedAt)
        .sort((a, b) => (a.pinned === b.pinned ? b.updatedAt - a.updatedAt : a.pinned ? -1 : 1)),
    [notes, ownerId]
  );

  if (mine.length === 0) {
    return (
      <EmptyState
        icon="notebook-outline"
        title={t("nt.noNotes")}
        hint={ownerId === "general" ? t("nt.generalNotesHint") : t("nt.notesHint")}
        actionLabel={t("nt.newNote")}
        onAction={() => open({ type: "noteEdit", ownerId })}
      />
    );
  }

  return (
    <View>
      <View style={{ marginBottom: 10 }}>
        <Btn small label={t("nt.newNote")} icon="plus" onPress={() => open({ type: "noteEdit", ownerId })} />
      </View>
      {mine.map((n: Note) => (
        <View key={n.id} style={{ marginBottom: 8 }}>
          <SwipeRow
            onTap={() => open({ type: "noteEdit", noteId: n.id, ownerId })}
            leftActions={[{ icon: n.pinned ? "pin-off-outline" : "pin", color: "#b97d10", onPress: () => saveNote({ id: n.id, ownerId, pinned: !n.pinned }) }]}
            rightActions={[
              {
                icon: "pencil-outline",
                color: "#4a7d9b",
                onPress: () => open({ type: "noteEdit", noteId: n.id, ownerId }),
              },
              {
                icon: "trash-can-outline",
                color: C.red,
                onPress: async () => {
                  const ok = await confirm(t("nt.deleteTitle"), t("nt.deleteMsg"), t("act.delete"), true);
                  if (ok) deleteNote(n.id);
                },
              },
            ]}
          >
            <Card>
              <View style={{ flexDirection: "row", gap: 10 }}>
                {!!n.color && <View style={[noteStyles.strip, { backgroundColor: n.color }]} />}
                <View style={{ flex: 1 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
                    <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                      {n.title || t("nt.untitled")}
                    </Text>
                    {n.pinned && <Icon name="pin" size={14} color="#b97d10" />}
                  </View>
                  {!!n.body && (
                    <Text style={{ fontSize: F.small, color: C.sub, marginTop: 2 }} numberOfLines={2}>
                      {n.body}
                    </Text>
                  )}
                  <View style={{ flexDirection: "row", alignItems: "center", gap: 6, marginTop: 6, flexWrap: "wrap" }}>
                    {n.tags.slice(0, 4).map((tag) => (
                      <Text key={tag} style={{ fontSize: F.tiny, color: C.brand, fontWeight: "700" }}>
                        #{tag}
                      </Text>
                    ))}
                    {n.tags.length > 4 && (
                      <Text style={{ fontSize: F.tiny, color: C.faint }}>+{n.tags.length - 4}</Text>
                    )}
                    <Text style={{ fontSize: F.tiny, color: C.faint, marginLeft: "auto" }}>{relTime(n.updatedAt)}</Text>
                  </View>
                </View>
              </View>
            </Card>
          </SwipeRow>
        </View>
      ))}
    </View>
  );
};

export const NOTE_COLOR_OPTIONS = NOTE_COLORS;

const noteStyles = StyleSheet.create({
  strip: { width: 4, borderRadius: 2, alignSelf: "stretch" },
});
