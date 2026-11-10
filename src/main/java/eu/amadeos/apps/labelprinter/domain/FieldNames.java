package eu.amadeos.apps.labelprinter.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The usual fields of address lists, under the names programs and people give their columns, so a design made
 * with one list works with another: «Vorname» finds "First Name", "Given Name" or "prénom".
 */
public final class FieldNames {
    private static final List<Set<String>> SAME = List.of(
            Set.of("salutation", "anrede", "civilité", "civilite", "saludo", "tratamiento", "titolo di cortesia",
                    "appellativo"),
            Set.of("prefix", "title prefix", "name prefix", "titel", "akademischer titel", "titre", "título", "titolo"),
            Set.of("first name", "firstname", "given name", "vorname", "prénom", "prenom", "nombre", "nome"),
            Set.of("last name", "lastname", "surname", "family name", "nachname", "familienname", "nom",
                    "nom de famille", "apellido", "apellidos", "cognome"),
            Set.of("name", "full name", "display name", "vollständiger name", "nom complet", "nombre completo",
                    "nome completo"),
            Set.of("company", "organization", "organisation", "firma", "unternehmen", "société", "societe",
                    "entreprise", "empresa", "azienda", "ditta"),
            Set.of("job title", "position", "funktion", "berufsbezeichnung", "fonction", "cargo", "puesto",
                    "qualifica", "ruolo"),
            Set.of("street", "address", "street address", "address 1", "home street", "business street",
                    "address 1 - street", "straße", "strasse", "anschrift", "rue", "adresse", "calle", "dirección",
                    "direccion", "via", "indirizzo"),
            Set.of("postcode", "postal code", "zip", "zip code", "home postal code", "business postal code",
                    "address 1 - postal code", "plz", "postleitzahl", "code postal", "cp", "código postal",
                    "codigo postal", "cap"),
            Set.of("city", "town", "home city", "business city", "address 1 - city", "ort", "stadt", "wohnort",
                    "ville", "ciudad", "localidad", "città", "citta", "località", "localita"),
            Set.of("region", "state", "province", "address 1 - region", "bundesland", "provinz", "région",
                    "provincia", "regione"),
            Set.of("country", "home country/region", "address 1 - country", "land", "staat", "pays", "país",
                    "pais", "paese", "nazione"),
            Set.of("phone", "telephone", "phone 1 - value", "mobile", "mobile phone", "telefon", "handy",
                    "mobil", "téléphone", "teléfono", "telefono", "cellulare"),
            Set.of("e-mail", "email", "mail", "e-mail address", "e-mail 1 - value", "e-mail-adresse", "courriel",
                    "correo", "correo electrónico", "posta elettronica"),
            Set.of("web", "website", "web page", "homepage", "url", "webseite", "site web", "sitio web", "sito web"));

    private FieldNames() {
    }

    /** Whether two column or field names mean the same: equal, or in the same group. */
    public static boolean same(String a, String b) {
        String x = normal(a);
        String y = normal(b);
        if (x.equals(y)) return true;
        return SAME.stream().anyMatch(group -> group.contains(x) && group.contains(y));
    }

    /** The column of {@code columns} that means the same as {@code field}. */
    public static Optional<String> match(String field, List<String> columns) {
        for (String c : columns) if (normal(c).equals(normal(field))) return Optional.of(c);
        for (String c : columns) if (same(field, c)) return Optional.of(c);
        return Optional.empty();
    }

    /** For each field of the design, the column that fills it; fields without one map to "". */
    public static Map<String, String> mapping(Iterable<String> fields, List<String> columns) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String f : fields) mapping.put(f, match(f, columns).orElse(""));
        return mapping;
    }

    private static String normal(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ");
    }
}
