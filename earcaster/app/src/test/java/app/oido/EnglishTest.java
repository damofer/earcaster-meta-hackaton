package app.oido;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The same spoken texts with the app in English (Lang). */
public class EnglishTest {

    @Before
    public void english() {
        Lang.setEnglish(true);
    }

    @After
    public void spanish() {
        Lang.setEnglish(false);
    }

    /** Monday, October 19, 2026 at the given time. */
    private static Calendar at(int hour, int minute) {
        return new GregorianCalendar(2026, Calendar.OCTOBER, 19, hour, minute);
    }

    @Test
    public void dayAndDate() {
        assertEquals("Today is Monday October 19", SpokenText.fill("Today is {day} {date}", at(10, 0)));
        // The Spanish names of the fields work too.
        assertEquals("Monday 2026", SpokenText.fill("{día} {año}", at(10, 0)));
    }

    @Test
    public void time() {
        assertEquals("It is 1:05 in the afternoon", SpokenText.fill("It is {time}", at(13, 5)));
        assertEquals("a quarter past 3 in the afternoon", SpokenText.time(15, 15));
        assertEquals("half past 8 in the morning", SpokenText.time(8, 30));
        assertEquals("12 o'clock noon", SpokenText.time(12, 0));
        assertEquals("10 o'clock at night", SpokenText.time(22, 0));
        assertEquals("4:45 in the early morning", SpokenText.time(4, 45));
    }

    @Test
    public void greeting() {
        assertEquals("Good morning, Mom", SpokenText.fill("{greeting}, Mom", at(9, 0)));
        assertEquals("Good afternoon", SpokenText.fill("{saludo}", at(12, 0)));
        assertEquals("Good evening", SpokenText.fill("{greeting}", at(21, 0)));
    }

    @Test
    public void visit() {
        long start = new GregorianCalendar(2026, Calendar.OCTOBER, 25, 13, 0).getTimeInMillis();
        SpokenText.Visit v = new SpokenText.Visit(start, start + 7_200_000L, false);
        assertEquals("Ana is coming on Sunday at 1 o'clock in the afternoon", SpokenText.visit("Ana", v, at(10, 0), ""));
        assertEquals("Ana will come soon", SpokenText.visit("Ana", null, at(10, 0), ""));
        long tomorrow = new GregorianCalendar(2026, Calendar.OCTOBER, 20, 17, 30).getTimeInMillis();
        assertEquals("tomorrow, Tuesday, October 20 at half past 5 in the afternoon",
                SpokenText.when(new SpokenText.Visit(tomorrow, tomorrow + 3_600_000L, false), at(10, 0)));
    }

    @Test
    public void doses() {
        assertEquals("It is not logged yet today. Ask Charles", SpokenText.dose(0, at(10, 0), "Ask Charles"));
        long nine = at(9, 5).getTimeInMillis();
        assertEquals("Yes, today it was logged at 9:05 in the morning", SpokenText.dose(nine, at(10, 0), ""));
        assertEquals("Noted: morning pill, at 10 o'clock in the morning", SpokenText.doseNoted("morning pill", at(10, 0)));
    }

    @Test
    public void helpAlert() {
        assertEquals("Do you need help? I am going to alert Charles and Lucy. If you are fine, say: I am fine.",
                SpokenText.helpQuestion(Arrays.asList("Charles", "Lucy")));
        assertEquals("Earcaster: Mary said \"I fell\" at 17:10. Call to check on them.",
                SpokenText.helpMessage("Mary", "I fell", at(17, 10)));
        assertTrue(SpokenText.cancelsHelp("I'm fine", false));
        assertTrue(SpokenText.cancelsHelp("no thank you", true));
        assertFalse(SpokenText.cancelsHelp("i fell", true));
    }

    @Test
    public void keepsApostrophes() {
        assertEquals("i'm fine don't worry", Texts.forGrammar("I’m fine, don't worry!"));
        assertEquals("rock n roll", Texts.forGrammar("rock 'n' roll"));
    }

    @Test
    public void weekAndAgo() {
        assertEquals("every day", Weekly.describe(Weekly.EVERY_DAY));
        assertEquals("Monday to Friday", Weekly.describe(Weekly.WEEKDAYS));
        assertEquals("on Mondays and Fridays",
                Weekly.describe(Weekly.bit(Calendar.MONDAY) | Weekly.bit(Calendar.FRIDAY)));
        assertEquals("5 minutes ago", Ago.text(0, 5 * 60_000L));
        assertEquals("1 day ago", Ago.text(0, 25 * 3_600_000L));
    }

    @Test
    public void aiQuestions() {
        assertEquals("is ana coming today", AiText.question("grace is ana coming today", "Grace", false, false));
        assertEquals("what time is it", AiText.question("hey grace what time is it", "Grace", false, false));
        assertEquals("is ana coming today", AiText.question("is ana coming today", "Grace", true, false));
        assertNull(AiText.question("the tv is loud", "Grace", true, false));
    }
}
