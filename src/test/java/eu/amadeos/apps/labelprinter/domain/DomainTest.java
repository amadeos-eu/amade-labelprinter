package eu.amadeos.apps.labelprinter.domain;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {
    static final SheetTemplate ADDRESS = new SheetTemplate("a4-70x37-3x8", SheetKind.ADDRESS, 210, 297, 3, 8, 70,
            37, 0, 0.5, 70, 37, 0, LabelShape.RECTANGLE, 0);
    static final SheetTemplate CARDS = new SheetTemplate("a4-85x55-2x5", SheetKind.BUSINESS_CARD, 210, 297, 2, 5,
            85, 55, 15, 13.5, 95, 55, 0, LabelShape.RECTANGLE, 0);

    static DataTable members() {
        return new DataTable("members.csv", List.of("First name", "Last name", "City", "Zip"), List.of(
                List.of("Anna", "Berger", "Ulm", "89073"),
                List.of("Bernd", "Albrecht", "Esslingen", "73728"),
                List.of("Clara", "Zeller", "Ulm", "89075"),
                List.of("Dieter", "Maier", "Berlin", "10115")));
    }

    // ---- sheets -----------------------------------------------------------------------------------------------

    @Test
    void sheetPositionsGoAcrossThenDown() {
        assertEquals(24, ADDRESS.count());
        assertEquals(new Rectangle2D.Double(0, 0.5, 70, 37), ADDRESS.label(0));
        assertEquals(new Rectangle2D.Double(140, 0.5, 70, 37), ADDRESS.label(2));
        assertEquals(new Rectangle2D.Double(0, 37.5, 70, 37), ADDRESS.label(3));
        assertThrows(IndexOutOfBoundsException.class, () -> ADDRESS.label(24));
    }

    @Test
    void mirroredIndexSwapsColumnsForTheBack() {
        assertEquals(1, CARDS.mirroredIndex(0));
        assertEquals(0, CARDS.mirroredIndex(1));
        assertEquals(3, CARDS.mirroredIndex(2));
    }

    @Test
    void gapsAndNames() {
        assertEquals(10, CARDS.gapX(), 1e-9);
        assertEquals(0, CARDS.gapY(), 1e-9);
        assertEquals("A4", CARDS.paperName());
        assertEquals("85 × 55 mm", CARDS.sizeText());
        assertEquals("2 × 5", CARDS.gridText());
        assertEquals("63.5", SheetTemplate.mm(63.5));
        assertEquals("70", SheetTemplate.mm(70.0));
    }

    @Test
    void roundLabelsShowTheirDiameter() {
        SheetTemplate round = new SheetTemplate("r", SheetKind.ROUND, 210, 297, 4, 6, 40, 40, 10, 10, 50, 47, 0,
                LabelShape.ELLIPSE, 0);
        assertEquals("Ø 40 mm", round.sizeText());
    }

    @Test
    void sheetsThatDoNotFitAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SheetTemplate("x", SheetKind.ADDRESS, 210, 297, 4,
                8, 70, 37, 0, 0, 70, 37, 0, LabelShape.RECTANGLE, 0));
        assertThrows(IllegalArgumentException.class, () -> new SheetTemplate("x", SheetKind.ADDRESS, 210, 297, 3,
                8, 70, 37, 0, 0, 60, 37, 0, LabelShape.RECTANGLE, 0));
        assertThrows(IllegalArgumentException.class, () -> new SheetTemplate("x", SheetKind.ADDRESS, 210, 297, 0,
                8, 70, 37, 0, 0, 70, 37, 0, LabelShape.RECTANGLE, 0));
    }

    @Test
    void radiusAndHoleAreKeptInRange() {
        SheetTemplate s = new SheetTemplate("x", SheetKind.MULTI_PURPOSE, 210, 297, 1, 1, 20, 10, 0, 0, 20, 10, 50,
                LabelShape.RECTANGLE, 5);
        assertEquals(5, s.radius(), 1e-9);
        assertEquals(0, s.hole(), 1e-9);
    }

    @Test
    void searchFindsSizesInManySpellings() {
        assertTrue(ADDRESS.matches(""));
        assertTrue(ADDRESS.matches("70x37"));
        assertTrue(ADDRESS.matches("70 × 37 mm"));
        assertTrue(ADDRESS.matches("70,2x36,8"));
        assertTrue(ADDRESS.matches("3x8"));
        assertTrue(ADDRESS.matches("24"));
        assertFalse(ADDRESS.matches("99x10"));
    }

    @Test
    void ownSheetsHaveTheirPrefix() {
        assertFalse(ADDRESS.isOwn());
        assertTrue(ADDRESS.withId("own-1").isOwn());
    }

    @Test
    void gridCentresTheLabels() {
        SheetTemplate g = SheetTemplate.grid("g", SheetKind.ADDRESS, 210, 297, 3, 7, 70, 42.3);
        assertEquals(0, g.left(), 1e-9);
        assertEquals(0.45, g.top(), 0.01);
    }

    @Test
    void papersAreRecognised() {
        assertTrue(SheetTemplate.Paper.LETTER.matches(215.9, 279.4));
        assertFalse(SheetTemplate.Paper.A4.matches(215.9, 279.4));
    }

    // ---- placeholders -----------------------------------------------------------------------------------------

    @Test
    void fieldsAreFoundInBothSpellings() {
        assertEquals(List.of("Name", "City"), List.copyOf(Placeholders.fields("«Name»\n<<City>> «Name»")));
    }

    @Test
    void fillReplacesKnownFieldsAndKeepsUnknownOnes() {
        Function<String, String> v = f -> f.equals("Name") ? "Anna" : null;
        assertEquals("Dear Anna, «City»", Placeholders.fill("Dear «Name», «City»", v, false));
    }

    @Test
    void emptyLinesAreLeftOut() {
        Map<String, String> m = Map.of("Name", "Anna Berger", "Company", "", "Street", "Hauptstr. 1");
        String text = "«Name»\n«Company»\n«Street»";
        assertEquals("Anna Berger\nHauptstr. 1", Placeholders.fill(text, m::get, true));
        assertEquals("Anna Berger\n\nHauptstr. 1", Placeholders.fill(text, m::get, false));
    }

    @Test
    void linesWithOnlyPunctuationBetweenEmptyFieldsAreEmpty() {
        Map<String, String> m = Map.of("Zip", "", "City", "");
        assertEquals("To", Placeholders.fill("To\n«Zip» – «City»", m::get, true));
    }

    @Test
    void linesWithoutFieldsStay() {
        assertEquals("Hello\n\nWorld", Placeholders.fill("Hello\n\nWorld", f -> "", true));
    }

    @Test
    void counterIsPadded() {
        assertEquals("007", Placeholders.counter(7, 3));
        assertEquals("1234", Placeholders.counter(1234, 2));
    }

    // ---- merge ------------------------------------------------------------------------------------------------

    @Test
    void withoutDataThereIsOneLabel() {
        LabelDesign d = LabelDesign.empty(ADDRESS);
        List<Merge.Label> labels = Merge.labels(DataTable.NONE, MergeSettings.ALL, d, LocalDate.of(2026, 11, 10),
                Locale.GERMANY);
        assertEquals(1, labels.size());
        assertNull(labels.getFirst().values().apply("Name"));
        assertEquals("1", labels.getFirst().values().apply(Placeholders.NUMBER));
        assertEquals("10.11.2026", labels.getFirst().values().apply(Placeholders.DATE));
    }

    @Test
    void everyRecordIsALabelWithCopies() {
        LabelDesign d = LabelDesign.empty(ADDRESS);
        List<Merge.Label> labels = Merge.labels(members(), MergeSettings.ALL.withCopies(2), d, LocalDate.now(),
                Locale.UK);
        assertEquals(8, labels.size());
        assertEquals("Anna", labels.get(1).values().apply("First name"));
        assertEquals("Bernd", labels.get(2).values().apply("First name"));
        assertEquals("4", labels.get(3).values().apply(Placeholders.NUMBER));
    }

    @Test
    void counterCountsFromItsStart() {
        LabelDesign d = LabelDesign.empty(ADDRESS).withCounter(98, 4);
        List<Merge.Label> labels = Merge.labels(members(), MergeSettings.ALL, d, LocalDate.now(), Locale.UK);
        assertEquals("0098", labels.get(0).values().apply(Placeholders.COUNTER));
        assertEquals("0101", labels.get(3).values().apply(Placeholders.COUNTER));
    }

    @Test
    void excludedRowsAreLeftOut() {
        assertEquals(List.of(0, 2, 3), Merge.rows(members(), MergeSettings.ALL.withExcluded(Set.of(1))));
    }

    @Test
    void filtersCompareColumns() {
        assertEquals(List.of(0, 2), Merge.rows(members(), MergeSettings.ALL.withFilter("City = ulm")));
        assertEquals(List.of(1, 3), Merge.rows(members(), MergeSettings.ALL.withFilter("City != Ulm")));
        assertEquals(List.of(1, 3), Merge.rows(members(), MergeSettings.ALL.withFilter("City ≠ Ulm")));
        assertEquals(List.of(1), Merge.rows(members(), MergeSettings.ALL.withFilter("City ~ ssl")));
    }

    @Test
    void freeTextFilterSearchesAllColumns() {
        assertEquals(List.of(3), Merge.rows(members(), MergeSettings.ALL.withFilter("berlin")));
        assertEquals(List.of(), Merge.rows(members(), MergeSettings.ALL.withFilter("Paris")));
    }

    @Test
    void sortingIsNatural() {
        assertEquals(List.of(1, 0, 3, 2), Merge.rows(members(), MergeSettings.ALL.withSortBy("Last name")));
        DataTable numbers = new DataTable("n", List.of("No"), List.of(List.of("10"), List.of("9"), List.of("100")));
        assertEquals(List.of(1, 0, 2), Merge.rows(numbers, MergeSettings.ALL.withSortBy("No")));
    }

    @Test
    void fieldsFindColumnsWithOtherNames() {
        DataTable data = members();
        assertEquals(3, Merge.column(data, MergeSettings.ALL, "Postcode").orElseThrow());
        assertEquals(0, Merge.column(data, MergeSettings.ALL, "Vorname").orElseThrow());
        assertEquals(2, Merge.column(data, MergeSettings.ALL, "Ort").orElseThrow());
    }

    @Test
    void theMappingWins() {
        DataTable data = members();
        MergeSettings m = MergeSettings.ALL.withMapping(Map.of("Name", "Last name", "City", ""));
        assertEquals(1, Merge.column(data, m, "Name").orElseThrow());
        assertTrue(Merge.column(data, m, "City").isEmpty());
    }

    @Test
    void unknownFieldsAreEmptyWithData() {
        List<Merge.Label> labels = Merge.labels(members(), MergeSettings.ALL, LabelDesign.empty(ADDRESS),
                LocalDate.now(), Locale.UK);
        assertEquals("", labels.getFirst().values().apply("Fax"));
    }

    @Test
    void fieldNamesKnowTheLanguages() {
        assertTrue(FieldNames.same("PLZ", "Postcode"));
        assertTrue(FieldNames.same("E-Mail", "email"));
        assertTrue(FieldNames.same("Ville", "Città"));
        assertFalse(FieldNames.same("City", "Street"));
        assertEquals(Map.of("Nachname", "Last name", "Fax", ""),
                FieldNames.mapping(List.of("Nachname", "Fax"), members().columns()));
    }

    @Test
    void mergeSettingsAreClamped() {
        assertEquals(1, MergeSettings.ALL.withCopies(0).copies());
        assertEquals(999, MergeSettings.ALL.withCopies(5000).copies());
        assertEquals("a", MergeSettings.ALL.withFilter("  a ").filter());
    }

    @Test
    void dataTablePadsRowsAndFindsColumns() {
        DataTable t = new DataTable("t", List.of("A", "B"), List.of(List.of("1"), List.of("1", "2", "3")));
        assertEquals("", t.value(0, 1));
        assertEquals("2", t.value(1, 1));
        assertEquals(1, t.column(" b ").orElseThrow());
        assertTrue(t.column("C").isEmpty());
    }

    // ---- print plan -------------------------------------------------------------------------------------------

    static List<Merge.Label> labels(int n) {
        return Merge.labels(new DataTable("n", List.of("No"),
                java.util.stream.IntStream.range(0, n).mapToObj(i -> List.of(Integer.toString(i))).toList()),
                MergeSettings.ALL, LabelDesign.empty(ADDRESS), LocalDate.now(), Locale.UK);
    }

    @Test
    void planCountsSheetsAndLeftOvers() {
        PrintPlan plan = new PrintPlan(ADDRESS, labels(46), 0);
        assertEquals(2, plan.sheets());
        assertEquals(2, plan.leftOver());
        PrintPlan started = new PrintPlan(ADDRESS, labels(46), 5);
        assertEquals(3, started.sheets());
        assertEquals(21, started.leftOver());
    }

    @Test
    void planSkipsUsedPositions() {
        PrintPlan plan = new PrintPlan(ADDRESS, labels(3), 2);
        assertTrue(plan.used(0, 1));
        assertFalse(plan.used(0, 2));
        assertFalse(plan.used(1, 0));
        assertNull(plan.at(0, 1));
        assertEquals("0", plan.at(0, 2).values().apply("No"));
        assertEquals("2", plan.at(0, 4).values().apply("No"));
        assertNull(plan.at(0, 5));
    }

    @Test
    void planStartStaysOnTheSheet() {
        assertEquals(23, new PrintPlan(ADDRESS, labels(1), 99).start());
        assertEquals(0, new PrintPlan(ADDRESS, labels(1), -3).start());
        assertEquals(1, new PrintPlan(ADDRESS, List.of(), 0).sheets());
    }

    // ---- design -----------------------------------------------------------------------------------------------

    @Test
    void businessCardsStartWithCropMarks() {
        assertEquals(CutGuides.CROP_MARKS, LabelDesign.empty(CARDS).cutGuides());
        assertEquals(CutGuides.NONE, LabelDesign.empty(ADDRESS).cutGuides());
    }

    @Test
    void twoSidedDesignsHaveABack() {
        LabelDesign d = LabelDesign.empty(CARDS);
        assertFalse(d.twoSided());
        LabelDesign two = d.withTwoSided(true);
        assertTrue(two.twoSided());
        assertEquals(Side.EMPTY.elements(), two.side(true).elements());
        assertFalse(two.withTwoSided(false).twoSided());
    }

    @Test
    void designFieldsComeFromTextsAndCodes() {
        Element.Text t = new Element.Text("t", new Box(1, 1, 50, 10), "«Name»\n«City»", TextStyle.DEFAULT, true,
                true);
        Element.Code c = new Element.Code("c", new Box(1, 1, 20, 20), CodeType.QR, "tel:«Phone»", Color.BLACK,
                false);
        LabelDesign d = LabelDesign.empty(CARDS).withTwoSided(true);
        d = d.withSide(false, d.front().with(t)).withSide(true, d.side(true).with(c));
        assertEquals(Set.of("Name", "City", "Phone"), d.fields());
    }

    @Test
    void sidesAddReplaceRemoveAndReorder() {
        Element a = new Element.Figure("a", new Box(0, 0, 5, 5), ShapeKind.RECTANGLE, null, Color.BLACK, 0.3, 0);
        Element b = new Element.Figure("b", new Box(0, 0, 5, 5), ShapeKind.ELLIPSE, null, Color.BLACK, 0.3, 0);
        Side s = Side.EMPTY.with(a).with(b);
        assertEquals(List.of("a", "b"), s.elements().stream().map(Element::id).toList());
        assertEquals(List.of("b", "a"), s.reordered("a", true).elements().stream().map(Element::id).toList());
        assertEquals(List.of("b", "a"), s.reordered("b", false).elements().stream().map(Element::id).toList());
        Side moved = s.with(a.withBox(new Box(3, 3, 5, 5)));
        assertEquals(3, moved.find("a").orElseThrow().box().x(), 1e-9);
        assertEquals(List.of("b"), s.without("a").elements().stream().map(Element::id).toList());
    }

    @Test
    void boxesKeepAMinimumSize() {
        Box b = new Box(1, 2, 0, -5);
        assertEquals(Box.MIN, b.w(), 1e-9);
        assertEquals(Box.MIN, b.h(), 1e-9);
        assertEquals(new Box(3, 4, 1, 1), b.moved(2, 2));
    }

    @Test
    void textStyleChanges() {
        TextStyle s = TextStyle.DEFAULT.withBold(true).withSize(14).withColor(Color.RED).withAlign(Align.CENTER);
        assertTrue(s.bold());
        assertEquals(14, s.size(), 1e-9);
        assertEquals(Color.RED, s.color());
        assertEquals(Align.CENTER, s.align());
    }

    @Test
    void documentsNameTheirPicturesUniquely() {
        LabelDocument doc = new LabelDocument(LabelDesign.empty(CARDS), Map.of(), DataSource.NONE,
                MergeSettings.ALL);
        LabelDocument.Named first = doc.withImage("logo.png", new byte[]{1});
        LabelDocument.Named second = first.document().withImage("logo.png", new byte[]{2});
        LabelDocument.Named same = second.document().withImage("logo.png", new byte[]{1});
        assertEquals("logo.png", first.name());
        assertFalse(second.name().equals("logo.png"));
        assertEquals("logo.png", same.name());
        assertEquals(2, second.document().images().size());
    }

    @Test
    void prunedDocumentsDropUnusedPictures() {
        LabelDocument doc = new LabelDocument(LabelDesign.empty(CARDS), Map.of(), DataSource.NONE,
                MergeSettings.ALL).withImage("logo.png", new byte[]{1}).document();
        assertTrue(doc.pruned().images().isEmpty());
        LabelDesign d = doc.design().withSide(false, Side.EMPTY.withBackground(Background.NONE.withImage(
                "logo.png")));
        assertEquals(Set.of("logo.png"), doc.withDesign(d).pruned().images().keySet());
    }

    @Test
    void dataSourcesHaveDefaults() {
        DataSource s = new DataSource(null, null, null, null, true);
        assertEquals(DataSource.Kind.NONE, s.kind());
        assertEquals("", s.path());
        assertEquals(DataSource.Kind.CONTACTS, DataSource.contacts("Club").kind());
    }

    @Test
    void enumKeysAreMessageKeys() {
        assertEquals("kind.business-card", SheetKind.BUSINESS_CARD.key());
        assertEquals("cut.crop-marks", CutGuides.CROP_MARKS.key());
        assertEquals("code.ean-13", CodeType.EAN_13.key());
        assertEquals("shape.rectangle", ShapeKind.RECTANGLE.key());
        assertTrue(CodeType.QR.square());
        assertFalse(CodeType.CODE_128.square());
    }
}
