package io.github.limehee.hookrouter.core.exception;

import java.io.Serial;

public class DuplicateFormatterException extends WebhookException {

    @Serial
    private static final long serialVersionUID = 2698878431720187464L;

    public DuplicateFormatterException(String platform, String typeId) {
        super(String.format(
            "Duplicate formatter registration detected for key [platform='%s', typeId='%s']. "
                + "Each (platform, typeId) combination must have exactly one formatter.",
            platform,
            typeId
        ));
    }
}
