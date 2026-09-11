package dev.untamed.domain.fire;

/** The outcome of applying the dousing rules to one observed position. */
public enum FireAction {

    /** Leave the position alone; it stays in the index. */
    NONE,

    /** Put the fire source out and remember that rain did it. */
    EXTINGUISH,

    /** Bring a rain-doused fire source back to life. */
    RELIGHT,

    /** Nothing of ours is here any more; drop the position from the index. */
    UNTRACK
}
