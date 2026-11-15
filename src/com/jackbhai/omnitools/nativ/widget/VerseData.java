package com.jackbhai.omnitools.nativ.widget;

import java.util.Calendar;

/**
 * Offline Verse of the Day pool: short public-domain lines only — the
 * King James Bible (1611 / public domain) and classic public-domain
 * renderings of the Bhagavad Gita in the spirit of Edwin Arnold's
 * "The Song Celestial" (1885). Each entry is { text, source label }.
 * The verse for a day is chosen deterministically by day-of-year, so
 * every install shows the same verse on the same day and it rolls over
 * at midnight via the widget update period.
 */
public final class VerseData {

    public static final String[][] VERSES = {
            // --- King James Bible (public domain) ---
            {"The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)"},
            {"Be still, and know that I am God.", "Psalm 46:10 (KJV)"},
            {"Thy word is a lamp unto my feet, and a light unto my path.", "Psalm 119:105 (KJV)"},
            {"This is the day which the Lord hath made; we will rejoice and be glad in it.", "Psalm 118:24 (KJV)"},
            {"The heavens declare the glory of God; and the firmament sheweth his handywork.", "Psalm 19:1 (KJV)"},
            {"Wait on the Lord: be of good courage, and he shall strengthen thine heart.", "Psalm 27:14 (KJV)"},
            {"In the beginning God created the heaven and the earth.", "Genesis 1:1 (KJV)"},
            {"The Lord bless thee, and keep thee: the Lord make his face shine upon thee.", "Numbers 6:24-25 (KJV)"},
            {"Be strong and of a good courage; be not afraid, neither be thou dismayed.", "Joshua 1:9 (KJV)"},
            {"Trust in the Lord with all thine heart; and lean not unto thine own understanding.", "Proverbs 3:5 (KJV)"},
            {"A merry heart doeth good like a medicine.", "Proverbs 17:22 (KJV)"},
            {"To every thing there is a season, and a time to every purpose under the heaven.", "Ecclesiastes 3:1 (KJV)"},
            {"I can do all things through Christ which strengtheneth me.", "Philippians 4:13 (KJV)"},
            {"Rejoice in the Lord alway: and again I say, Rejoice.", "Philippians 4:4 (KJV)"},
            {"Let all your things be done with charity.", "1 Corinthians 16:14 (KJV)"},
            {"Ask, and it shall be given you; seek, and ye shall find; knock, and it shall be opened unto you.", "Matthew 7:7 (KJV)"},
            {"Come unto me, all ye that labour and are heavy laden, and I will give you rest.", "Matthew 11:28 (KJV)"},
            {"For God so loved the world, that he gave his only begotten Son.", "John 3:16 (KJV)"},
            // --- Bhagavad Gita (public-domain renderings) ---
            {"Never the spirit was born; the spirit shall cease to be never.", "Bhagavad Gita 2.20"},
            {"As a man casts off worn garments, so the soul casts off worn-out bodies.", "Bhagavad Gita 2.22"},
            {"Thy business is with the action only, never with its fruits.", "Bhagavad Gita 2.47"},
            {"Perform thy work, abandoning attachment, steadfast in yoga.", "Bhagavad Gita 2.48"},
            {"The contacts of the senses give cold and heat, pleasure and pain; they come and go — endure them bravely.", "Bhagavad Gita 2.14"},
            {"Therefore, always perform the work that must be done, without attachment.", "Bhagavad Gita 3.19"},
            {"Better is one's own duty, though imperfect, than the duty of another well performed.", "Bhagavad Gita 3.35"},
            {"In this world there is nothing so purifying as knowledge.", "Bhagavad Gita 4.38"},
            {"Whenever dharma declines, O Bharata, then I manifest Myself.", "Bhagavad Gita 4.7"},
            {"He who acts, offering his actions to the Divine, is untouched by sin, as a lotus leaf by water.", "Bhagavad Gita 5.10"},
            {"The wise see the same in all beings, high and low.", "Bhagavad Gita 5.18"},
            {"Let a man lift himself by his own self; let him not debase himself.", "Bhagavad Gita 6.5"},
            {"For the mind is the friend of the self, and the mind is also its enemy.", "Bhagavad Gita 6.6"},
            {"Yoga is the destroyer of pain for him who is moderate in eating and in recreation.", "Bhagavad Gita 6.17"},
            {"There is nothing higher than Me; all this is strung on Me as pearls on a thread.", "Bhagavad Gita 7.7"},
            {"I am the Self, seated in the hearts of all beings.", "Bhagavad Gita 10.20"},
            {"He who hates no creature, who is friendly and compassionate — he is dear to Me.", "Bhagavad Gita 12.13"},
            {"Leave all lesser duties, come to Me alone for shelter; I will free thee from all sorrow.", "Bhagavad Gita 18.66"},
    };

    private VerseData() {}

    /** Deterministic verse index for today (day-of-year based). */
    public static int todayIndex() {
        int doy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR); // 1..366
        return (doy - 1) % VERSES.length;
    }

    public static String[] today() {
        return VERSES[todayIndex()];
    }
}
