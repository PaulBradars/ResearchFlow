package researchflow.domain;

/** Legacy snapshots cannot establish full response membership, including blank responses. */
public enum DatasetFreshness {
    NO_ACTIVE_VERSION, CURRENT, STALE, UNKNOWN_LEGACY
}
