package eu.amadeos.apps.labelprinter;

import eu.amadeos.apps.labelprinter.bootstrap.LabelPrinterModule;
import eu.amadeos.apps.labelprinter.domain.CodeType;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.LabelShape;
import eu.amadeos.apps.labelprinter.domain.ShapeKind;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.service.ContactColumn;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(LabelPrinterModule.class, LabelPrinterModule.MESSAGES,
                Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(LabelPrinterModule.class, LabelPrinterModule.MESSAGES));
    }

    @Test
    void everyKeyTheCodeBuildsExists() {
        List<String> keys = new ArrayList<>();
        for (SheetKind k : SheetKind.values()) keys.add(k.key());
        for (CutGuides c : CutGuides.values()) keys.add(c.key());
        for (CodeType c : CodeType.values()) keys.add(c.key());
        for (ShapeKind s : ShapeKind.values()) keys.add(s.key());
        for (ContactColumn c : ContactColumn.values()) keys.add(c.key());
        for (LabelShape s : LabelShape.values()) keys.add("label-shape." + s.name().toLowerCase(Locale.ROOT));
        for (String f : List.of("date", "counter", "number")) keys.add("field." + f);
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (String key : keys) assertTrue(texts.has(key), language + " " + key);
        }
    }

    /** No two entries share a mnemonic letter in the menu bar or in one menu. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = Map.of(
                "bar", List.of("menu.file", "menu.edit", "menu.insert", "menu.label", "menu.view", "menu.options",
                        "menu.help"),
                "file", List.of("menu.file.new", "menu.file.open", "menu.file.recent", "menu.file.save",
                        "menu.file.save-as", "menu.file.print", "menu.file.export-pdf", "menu.file.exit"),
                "edit", List.of("menu.edit.undo", "menu.edit.redo", "menu.edit.duplicate", "menu.edit.delete",
                        "menu.edit.to-front", "menu.edit.to-back"),
                "insert", List.of("menu.insert.text", "menu.insert.field", "menu.insert.picture",
                        "menu.insert.shape", "menu.insert.barcode", "menu.insert.qr"),
                "label", List.of("menu.label.sheet", "menu.label.data", "menu.label.reload", "menu.label.previous",
                        "menu.label.next", "menu.label.front", "menu.label.back"),
                "view", List.of("menu.view.zoom-in", "menu.view.zoom-out", "menu.view.zoom-fit"),
                "help", List.of("menu.help.handbook", "menu.help.about"),
                "data", List.of("data.none", "data.file", "data.choose", "data.contacts", "data.typed"),
                "print", List.of("print.all", "print.current", "print.fill", "print.test-page", "print.ok"),
                "sheet", List.of("sheet.search", "sheet.measure", "sheet.edit", "sheet.delete"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertNull(before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void formatsWork() {
        Messages de = texts(Language.GERMAN);
        assertEquals("1 Bogen an Büro geschickt.", de.format("status.printed", 1, "Büro"));
        assertEquals("Datensatz 3 von 46", de.format("record.of", 3, 46));
        Messages en = texts(Language.ENGLISH);
        assertEquals("Print 2 Sheets", Mnemonics.strip(en.format("print.ok.sheets", 2)));
        assertEquals("2 sheets · 5 labels left over on the last sheet", en.format("print.result.text", 2, 5));
        Messages fr = texts(Language.FRENCH);
        assertEquals("Impossible d'ouvrir a.label : x", fr.format("error.open", "a.label", "x"));
        Messages it = texts(Language.ITALIAN);
        assertTrue(it.format("print.result.text", 1, 3).contains("sull'ultimo"));
        for (Language language : Language.values()) {
            Messages m = texts(language);
            assertFalse(m.format("sheet.details", "A4", "3 × 8", 24, "70 × 37 mm", "0", "0.5", "0", "0")
                    .contains("{"), language.toString());
            assertFalse(m.format("status.sheet", "A4", "3 × 8", "70 × 37 mm", 2).contains("{"), language.toString());
        }
    }

    @Test
    void contactColumnsHaveDifferentNamesInEachLanguage() {
        for (Language language : Language.values()) {
            Messages m = texts(language);
            Set<String> names = new HashSet<>();
            for (ContactColumn c : ContactColumn.values()) assertTrue(names.add(m.get(c.key())), language + " " + c);
        }
    }
}
