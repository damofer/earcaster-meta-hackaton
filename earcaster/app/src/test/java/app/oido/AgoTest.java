package app.oido;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AgoTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void minutesHoursAndDays() {
        assertEquals("hace un momento", Ago.text(NOW - 20_000, NOW));
        assertEquals("hace 1 minuto", Ago.text(NOW - 60_000, NOW));
        assertEquals("hace 59 minutos", Ago.text(NOW - 59 * 60_000L, NOW));
        assertEquals("hace 2 horas", Ago.text(NOW - 2 * 3_600_000L - 5 * 60_000L, NOW));
        assertEquals("hace 1 día", Ago.text(NOW - 30 * 3_600_000L, NOW));
        assertEquals("hace 3 días", Ago.text(NOW - 3 * 86_400_000L, NOW));
    }

    @Test
    public void futureCountsAsNow() {
        assertEquals("hace un momento", Ago.text(NOW + 60_000, NOW));
    }
}
