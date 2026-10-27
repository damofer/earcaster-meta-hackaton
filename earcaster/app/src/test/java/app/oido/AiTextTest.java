package app.oido;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.Test;

/** Spec 017: when what is heard is for the AI, the reply frames and the current context. */
public class AiTextTest {

    @Test
    public void conElNombre() {
        assertEquals("ana viene hoy", AiText.question("clara ana viene hoy", "Clara", false, false));
        assertEquals("qué hora es", AiText.question("oye clara qué hora es", "Clara", false, false));
        assertEquals("cuándo viene", AiText.question("Clára, ¿cuándo viene?", "clara", false, false));
        assertEquals("quién es", AiText.question("doña rosa quién es", "Doña Rosa", false, false));
    }

    @Test
    public void soloElNombre() {
        assertEquals("", AiText.question("clara", "Clara", false, false));
        assertEquals("", AiText.question("ay clara", "Clara", false, false));
    }

    @Test
    public void elNombreMuyAtrasNoCuenta() {
        assertNull(AiText.question("la casa de mi amiga clara es bonita", "Clara", false, false));
        assertNull(AiText.question("ana viene hoy", "Clara", false, false));
        assertNull(AiText.question("", "Clara", true, true));
    }

    @Test
    public void esperandoRespuesta() {
        assertEquals("sí", AiText.question("sí", "Clara", false, true));
    }

    @Test
    public void cualquierPregunta() {
        assertEquals("cuándo viene ana", AiText.question("cuándo viene ana", "Clara", true, false));
        assertEquals("por qué estoy aquí", AiText.question("por qué estoy aquí", "Clara", true, false));
        assertNull(AiText.question("por la tarde llueve", "Clara", true, false));
        assertNull(AiText.question("qué bien", "Clara", true, false));
        assertNull(AiText.question("cuándo viene ana", "Clara", false, false));
    }

    private static byte[] frames(Object... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(out);
        for (int i = 0; i < parts.length; i += 2) {
            byte[] body = parts[i + 1] instanceof String
                    ? ((String) parts[i + 1]).getBytes(StandardCharsets.UTF_8) : (byte[]) parts[i + 1];
            d.write((Character) parts[i]);
            d.writeInt(body.length);
            d.write(body);
        }
        return out.toByteArray();
    }

    @Test
    public void leeLosTrozos() throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                frames('T', "Sí, Mari.", 'A', new byte[]{1, 2, 3, 4}, 'E', "{\"ok\":true}")));
        AiText.Frame f = AiText.read(in);
        assertEquals(AiText.TEXT, f.type);
        assertEquals("Sí, Mari.", f.text());
        f = AiText.read(in);
        assertEquals(AiText.AUDIO, f.type);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, f.body);
        assertEquals(AiText.END, AiText.read(in).type);
        assertNull(AiText.read(in));
    }

    @Test
    public void unTrozoCortadoEsUnError() throws IOException {
        byte[] all = frames('T', "Hola");
        byte[] cut = java.util.Arrays.copyOf(all, all.length - 1);
        try {
            AiText.read(new DataInputStream(new ByteArrayInputStream(cut)));
            fail();
        } catch (IOException expected) {
        }
        try {
            AiText.read(new DataInputStream(new ByteArrayInputStream(new byte[]{'A', 0x7f, 0, 0, 0})));
            fail();
        } catch (IOException expected) {
        }
    }

    /** Monday, October 19, 2026. */
    private static Calendar at(int day, int hour, int minute) {
        return new GregorianCalendar(2026, Calendar.OCTOBER, day, hour, minute);
    }

    @Test
    public void momento() {
        assertEquals("lunes 19 de octubre de 2026, las 10 y cuarto de la mañana", SpokenText.moment(at(19, 10, 15)));
    }

    @Test
    public void cuandoEsLaVisita() {
        Calendar now = at(19, 10, 0);
        long tomorrow5 = at(20, 17, 0).getTimeInMillis();
        assertEquals("mañana martes 20 de octubre a las 5 en punto de la tarde",
                SpokenText.when(new SpokenText.Visit(tomorrow5, tomorrow5 + 3_600_000, false), now));
        long today = at(19, 13, 30).getTimeInMillis();
        assertEquals("hoy lunes 19 de octubre a la 1 y media de la tarde",
                SpokenText.when(new SpokenText.Visit(today, today + 3_600_000, false), now));
        long friday = at(23, 0, 0).getTimeInMillis();
        assertEquals("el viernes 23 de octubre",
                SpokenText.when(new SpokenText.Visit(friday, friday + 86_400_000, true), now));
        long started = at(19, 9, 0).getTimeInMillis();
        assertEquals("ahora", SpokenText.when(new SpokenText.Visit(started, started + 7_200_000, false), now));
        assertNull(SpokenText.when(new SpokenText.Visit(started, started + 1_800_000, false), now));
    }
}
