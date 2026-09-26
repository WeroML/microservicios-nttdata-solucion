package tacos.web.api;

import tacos.api.error.BadRequestException;

// TC-19/TC-21/TC-23: page y size validados contra un máximo configurable.
public final class Paging {

    private Paging() {
    }

    public static void validate(int page, int size, int maxSize) {
        if (page < 0 || size < 1 || size > maxSize) {
            throw new BadRequestException("INVALID_PAGINATION",
                "page must be >= 0 and size between 1 and " + maxSize + ".");
        }
    }
}
