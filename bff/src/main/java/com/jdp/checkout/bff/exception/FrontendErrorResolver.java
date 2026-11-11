package com.jdp.checkout.bff.exception;

import com.jdp.checkout.bff.dto.response.DisplayType;
import com.jdp.checkout.bff.dto.response.ErrorResponse.Button;
import com.jdp.checkout.bff.dto.response.ErrorResponse.FrontendError;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Resolves each {@link ErrorCode}, for the request locale, into the HTTP status + what the frontend shows.
 * The switch is exhaustive: adding an ErrorCode without an entry does not compile.
 * Texts live in {@code messages*.properties} (pt-BR default, en), keyed by {@code error.<CODE>.title|description}
 * and {@code button.<name>}; codes, statuses and routes never change with the language.
 */
@Component
public class FrontendErrorResolver {

    public static final String RETRY = "RETRY";
    private static final String PAYMENT_PAGE = "/checkout/pagamento";
    private static final String PLANS_PAGE = "/planos";

    private final MessageSource messages;

    public FrontendErrorResolver(MessageSource messages) {
        this.messages = messages;
    }

    public record ResolvedError(int httpStatus, DisplayType displayType, FrontendError frontendError) {
    }

    private record Action(String messageKey, String url) {
        static final Action TRY_AGAIN = new Action("button.try-again", RETRY);
        static final Action CHANGE_METHOD = new Action("button.change-method", PAYMENT_PAGE);
        static final Action CHOOSE_PLAN = new Action("button.choose-plan", PLANS_PAGE);
        static final Action REVIEW_DATA = new Action("button.review-data", PAYMENT_PAGE);
        static final Action MY_SUBSCRIPTION = new Action("button.my-subscription", "/minha-conta");
    }

    public ResolvedError resolve(ErrorCode code, Locale locale) {
        return switch (code) {
            case INVALID_REQUEST -> error(code, locale, 400, DisplayType.INTERVENTION, "P-1000", Action.TRY_AGAIN, null);
            case PAYMENT_DECLINED -> error(code, locale, 422, DisplayType.BLOCK, "P-1001", Action.TRY_AGAIN, Action.CHANGE_METHOD);
            case PRODUCT_NOT_FOUND -> error(code, locale, 422, DisplayType.BLOCK, "P-1002", Action.CHOOSE_PLAN, null);
            case CART_NOT_FOUND -> error(code, locale, 404, DisplayType.BLOCK, "P-1003", Action.CHOOSE_PLAN, null);
            case PAYMENT_METHOD_NOT_ALLOWED -> error(code, locale, 422, DisplayType.INTERVENTION, "P-1004", Action.CHANGE_METHOD, null);
            case INVALID_PAYMENT_DATA -> error(code, locale, 400, DisplayType.INTERVENTION, "P-1005", Action.REVIEW_DATA, null);
            case CART_ALREADY_PAID -> error(code, locale, 409, DisplayType.BLOCK, "P-1006", Action.MY_SUBSCRIPTION, null);
            case SERVICE_UNAVAILABLE -> error(code, locale, 503, DisplayType.BLOCK, "P-9001", Action.TRY_AGAIN, null);
            case INTERNAL_ERROR -> error(code, locale, 500, DisplayType.BLOCK, "P-9999", Action.TRY_AGAIN, null);
        };
    }

    private ResolvedError error(ErrorCode code, Locale locale, int status, DisplayType displayType, String frontendCode,
                                Action primary, Action secondary) {
        return new ResolvedError(status, displayType, new FrontendError(frontendCode,
                text("error." + code.name() + ".title", locale),
                text("error." + code.name() + ".description", locale),
                button(primary, locale),
                secondary == null ? null : button(secondary, locale)));
    }

    private Button button(Action action, Locale locale) {
        return new Button(text(action.messageKey(), locale), action.url());
    }

    private String text(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }
}
