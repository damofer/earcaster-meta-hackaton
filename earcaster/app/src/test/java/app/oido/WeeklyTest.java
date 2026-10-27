package app.oido;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.Test;

/** When something that repeats every week is due: recurring visits (spec 014) and reminders (spec 006). */
public class WeeklyTest {

    private static Calendar oct(int day, int hour, int minute) {
        return new GregorianCalendar(2026, Calendar.OCTOBER, day, hour, minute);
    }

    @Test
    public void proximoDomingo() {
        // Today is Monday the 19th at 10:00; recurring visit on Sundays at 13:00.
        long next = Weekly.next(Weekly.bit(Calendar.SUNDAY), 13 * 60, 0, oct(19, 10, 0));
        assertEquals(oct(25, 13, 0).getTimeInMillis(), next);
    }

    @Test
    public void visitaFijaEnCurso() {
        // Sunday the 25th at 14:00, the 13:00 visit lasts two hours: still in progress.
        long next = Weekly.next(Weekly.bit(Calendar.SUNDAY), 13 * 60, 2 * 3_600_000L, oct(25, 14, 0));
        assertEquals(oct(25, 13, 0).getTimeInMillis(), next);
    }

    @Test
    public void recordatorioALaHoraExactaPasaAlDiaSiguiente() {
        long next = Weekly.next(Weekly.EVERY_DAY, 8 * 60, 0, oct(20, 8, 0));
        assertEquals(oct(21, 8, 0).getTimeInMillis(), next);
        assertEquals(oct(20, 8, 0).getTimeInMillis(), Weekly.next(Weekly.EVERY_DAY, 8 * 60, 0, oct(20, 7, 59)));
    }

    @Test
    public void deLunesAViernesSaltaElFinDeSemana() {
        // Friday the 23rd at 9:00, reminder Monday to Friday at 8:00: Monday the 26th.
        assertEquals(oct(26, 8, 0).getTimeInMillis(), Weekly.next(Weekly.WEEKDAYS, 8 * 60, 0, oct(23, 9, 0)));
    }

    @Test
    public void sinDias() {
        assertEquals(-1, Weekly.next(0, 8 * 60, 0, oct(19, 10, 0)));
    }

    @Test
    public void comoSeDicenLosDias() {
        assertEquals("todos los días", Weekly.describe(Weekly.EVERY_DAY));
        assertEquals("de lunes a viernes", Weekly.describe(Weekly.WEEKDAYS));
        assertEquals("los fines de semana", Weekly.describe(Weekly.WEEKEND));
        assertEquals("los domingos", Weekly.describe(Weekly.bit(Calendar.SUNDAY)));
        assertEquals("los lunes, miércoles y viernes", Weekly.describe(
                Weekly.bit(Calendar.MONDAY) | Weekly.bit(Calendar.WEDNESDAY) | Weekly.bit(Calendar.FRIDAY)));
    }

    @Test
    public void reloj() {
        assertEquals("8:05", Weekly.clock(8 * 60 + 5));
        assertEquals("13:00", Weekly.clock(13 * 60));
    }
}
