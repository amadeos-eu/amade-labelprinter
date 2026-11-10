package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.commons.contacts.ContactCard;

import java.util.Locale;
import java.util.function.Function;

/** The columns a contact gives for mail merge. Their names come from the messages, in the user's language. */
public enum ContactColumn {
    PREFIX(c -> c.parts().prefix()),
    FIRST_NAME(c -> join(c.parts().given(), c.parts().additional())),
    LAST_NAME(c -> join(c.parts().family(), c.parts().suffix())),
    NAME(ContactCard::name),
    COMPANY(ContactCard::organization),
    JOB_TITLE(ContactCard::title),
    ADDRESS_EXTRA(c -> c.address().map(ContactCard.Address::extended).orElse("")),
    STREET(c -> c.address().map(a -> a.street().isEmpty() ? a.poBox() : a.street()).orElse("")),
    POSTCODE(c -> c.address().map(ContactCard.Address::postalCode).orElse("")),
    CITY(c -> c.address().map(ContactCard.Address::locality).orElse("")),
    REGION(c -> c.address().map(ContactCard.Address::region).orElse("")),
    COUNTRY(c -> c.address().map(ContactCard.Address::country).orElse("")),
    PHONE(c -> c.phone().orElse("")),
    EMAIL(c -> c.email().orElse("")),
    GROUPS(c -> String.join(", ", c.groups()));

    private final Function<ContactCard, String> value;

    ContactColumn(Function<ContactCard, String> value) {
        this.value = value;
    }

    public String value(ContactCard card) {
        return value.apply(card);
    }

    public String key() {
        return "column." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String join(String a, String b) {
        return (a + " " + b).strip();
    }
}
