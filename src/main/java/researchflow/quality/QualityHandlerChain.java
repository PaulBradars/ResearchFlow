package researchflow.quality;

/** Assembles the core deterministic quality-handler chain in a fixed, documented order. */
public final class QualityHandlerChain {
    private QualityHandlerChain() { }

    public static QualityHandler buildDefault() {
        var head = new MissingRequiredHandler();
        head.linkTo(new InvalidRangeHandler())
                .linkTo(new DuplicateResponseHandler())
                .linkTo(new OutlierHandler())
                .linkTo(new FastSubmissionHandler());
        return head;
    }
}
