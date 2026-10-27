package app.oido;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.Test;

/** Criteria from spec 001 (R2, R3), 014 (visits) and 007 (doses) for the spoken text. */
public class SpokenTextTest {

    /** Monday, October 19, 2026 at the given time. */
    private static Calendar at(int hour, int minute) {
        return new GregorianCalendar(2026, Calendar.OCTOBER, 19, hour, minute);
    }

    @Test
    public void diaYFecha() {
        assertEquals("Hoy es lunes 19 de octubre", SpokenText.fill("Hoy es {dia} {fecha}", at(10, 0)));
    }

    @Test
    public void horaDeLaTarde() {
        assertEquals("Son la 1 y 5 de la tarde", SpokenText.fill("Son {hora}", at(13, 5)));
        assertEquals("las 5 y 10 de la tarde", SpokenText.fill("{hora}", at(17, 10)));
    }

    @Test
    public void cuartoMediaYEnPunto() {
        assertEquals("las 3 y cuarto de la tarde", SpokenText.time(15, 15));
        assertEquals("las 8 y media de la mañana", SpokenText.time(8, 30));
        assertEquals("las 12 en punto del mediodía", SpokenText.time(12, 0));
        assertEquals("las 10 en punto de la noche", SpokenText.time(22, 0));
        assertEquals("las 12 y 20 de la noche", SpokenText.time(0, 20));
        assertEquals("las 4 y 45 de la madrugada", SpokenText.time(4, 45));
    }

    @Test
    public void saludoSegunLaHora() {
        assertEquals("Buenos días, mamá", SpokenText.fill("{saludo}, mamá", at(9, 0)));
        assertEquals("Buenas tardes", SpokenText.fill("{saludo}", at(12, 0)));
        assertEquals("Buenas noches", SpokenText.fill("{saludo}", at(21, 0)));
        assertEquals("Buenas noches", SpokenText.fill("{saludo}", at(3, 0)));
    }

    @Test
    public void conTildeYMayusculas() {
        assertEquals("lunes 2026 2026", SpokenText.fill("{Día} {año} { ANO }", at(10, 0)));
    }

    @Test
    public void datoDesconocidoSeQuedaIgual() {
        assertEquals("Hola {nombre}", SpokenText.fill("Hola {nombre}", at(10, 0)));
    }

    @Test
    public void textoSinDatosOVacio() {
        assertEquals("Las llaves están en el gancho", SpokenText.fill("  Las llaves están en el gancho ", at(10, 0)));
        assertEquals("", SpokenText.fill(null, at(10, 0)));
        assertEquals("", SpokenText.fill("", at(10, 0)));
    }

    // --- Spec 014: «¿Cuándo viene Ana?» (When is Ana coming?) ---

    private static long ms(int month, int day, int hour, int minute) {
        return new GregorianCalendar(2026, month, day, hour, minute).getTimeInMillis();
    }

    private static SpokenText.Visit visit(int month, int day, int hour, int minute) {
        long start = ms(month, day, hour, minute);
        return new SpokenText.Visit(start, start + 2 * 3_600_000L, false);
    }

    @Test
    public void visitaElDomingo() {
        // Criterion 1: today is Monday the 19th, visit on Sunday the 25th at 13:00.
        assertEquals("Ana viene el domingo a la 1 en punto de la tarde",
                SpokenText.visit("Ana", visit(Calendar.OCTOBER, 25, 13, 0), at(10, 0), ""));
    }

    @Test
    public void visitaHoyYManana() {
        assertEquals("Ana viene hoy a la 1 en punto de la tarde",
                SpokenText.visit("Ana", visit(Calendar.OCTOBER, 19, 13, 0), at(10, 0), ""));
        assertEquals("Ana viene mañana a las 5 y media de la tarde",
                SpokenText.visit("Ana", visit(Calendar.OCTOBER, 20, 17, 30), at(22, 0), ""));
    }

    @Test
    public void visitaEnCurso() {
        // Criterion 6: it started at 13:00 and it is 14:00.
        assertEquals("Ana está de visita hoy",
                SpokenText.visit("Ana", visit(Calendar.OCTOBER, 19, 13, 0), at(14, 0), ""));
    }

    @Test
    public void visitaLejana() {
        assertEquals("Ana viene el 3 de noviembre",
                SpokenText.visit("Ana", visit(Calendar.NOVEMBER, 3, 13, 0), at(10, 0), ""));
    }

    @Test
    public void visitaDeTodoElDia() {
        long start = ms(Calendar.OCTOBER, 20, 0, 0);
        SpokenText.Visit allDay = new SpokenText.Visit(start, start + 86_400_000L, true);
        assertEquals("Ana viene mañana", SpokenText.visit("Ana", allDay, at(10, 0), ""));
        long yesterday = ms(Calendar.OCTOBER, 18, 0, 0);
        SpokenText.Visit weekend = new SpokenText.Visit(yesterday, yesterday + 2 * 86_400_000L, true);
        assertEquals("Ana está de visita hoy", SpokenText.visit("Ana", weekend, at(10, 0), ""));
    }

    @Test
    public void sinVisitaUsaLaReserva() {
        // Criterion 3, and R5: with no fallback written, it says «Ana vendrá pronto» (Ana will come soon).
        assertEquals("Ana te llama pronto", SpokenText.visit("Ana", null, at(10, 0), "Ana te llama pronto"));
        assertEquals("Ana vendrá pronto", SpokenText.visit("Ana", null, at(10, 0), ""));
    }

    @Test
    public void datoVisitaEnElTexto() {
        SpokenText.Sources agenda = (name, now) ->
                name.equals("Ana") ? visit(Calendar.OCTOBER, 25, 13, 0) : null;
        assertEquals("Ana viene el domingo a la 1 en punto de la tarde. Te quiere mucho",
                SpokenText.fill("{visita:Ana|Ana te llama pronto}. Te quiere mucho", at(10, 0), agenda));
        assertEquals("Pedro te llama pronto",
                SpokenText.fill("{Visita: Pedro | Pedro te llama pronto}", at(10, 0), agenda));
        // R9: if the schedule fails, a date is never made up.
        SpokenText.Sources broken = (name, now) -> { throw new SecurityException("sin permiso"); };
        assertEquals("Ana te llama pronto", SpokenText.fill("{visita:Ana|Ana te llama pronto}", at(10, 0), broken));
        assertEquals("Ana vendrá pronto", SpokenText.fill("{visita:Ana}", at(10, 0)));
    }

    @Test
    public void nombreComoPalabraCompleta() {
        // R7: ignoring accents and case, and «Mariana» is not «Ana».
        assertTrue(SpokenText.mentions("Visita de Ána", "ana"));
        assertTrue(SpokenText.mentions("Ana viene a comer 🍲", "Ana"));
        assertTrue(SpokenText.mentions("Comida con Ana María", "ana maría"));
        assertFalse(SpokenText.mentions("Visita de Mariana", "Ana"));
        assertFalse(SpokenText.mentions("Dentista", "Ana"));
    }

    // --- Spec 007: «¿Ya tomé la pastilla?» (Did I take my pill?) ---

    @Test
    public void tomaAnotada() {
        // Criterion 1: logged at 9:05, asked at 11:00.
        long at = ms(Calendar.OCTOBER, 19, 9, 5);
        assertEquals("Sí, hoy está anotada a las 9 y 5 de la mañana", SpokenText.dose(at, at(11, 0), ""));
        SpokenText.Sources log = new SpokenText.Sources() {
            @Override public SpokenText.Visit nextVisit(String name, Calendar now) { return null; }
            @Override public long doseToday(String name, Calendar now) { return name.equals("pastilla") ? at : 0; }
        };
        assertEquals("Sí, hoy está anotada a las 9 y 5 de la mañana",
                SpokenText.fill("{toma:pastilla|Pregúntale a Carlos}", at(11, 0), log));
    }

    @Test
    public void tomaSinAnotarNuncaDiceTomala() {
        // Criterion 2 and R4: with no entry it never says «tómala» (take it) or «no la has tomado» (you haven't taken it).
        String said = SpokenText.fill("{toma:pastilla|Pregúntale a Carlos}", at(11, 0), (name, now) -> null);
        assertEquals("Hoy todavía no está anotada. Pregúntale a Carlos", said);
        assertEquals("Hoy todavía no está anotada", SpokenText.fill("{toma:pastilla}", at(11, 0)));
        assertFalse(said.toLowerCase().contains("tómala"));
        assertFalse(said.toLowerCase().contains("no la has tomado"));
    }

    @Test
    public void tomaDeAyerNoCuentaHoy() {
        // Criterion 3: yesterday's entry is not today's.
        assertFalse(SpokenText.sameDay(at(11, 0), ms(Calendar.OCTOBER, 18, 21, 0)));
        assertTrue(SpokenText.sameDay(at(11, 0), ms(Calendar.OCTOBER, 19, 0, 5)));
    }

    @Test
    public void tomaAnotadaPorVoz() {
        // Spec 008: «ya me la tomé» (I already took it) logs it and says so.
        assertEquals("Lo anoto: pastilla de la mañana, a las 9 y 5 de la mañana",
                SpokenText.doseNoted("pastilla de la mañana", at(9, 5)));
    }

    // --- Spec 009: help alert ---

    @Test
    public void avisoDeAyuda() {
        assertEquals("¿Necesitas ayuda? Voy a avisar a Carlos y Lucía. Si estás bien, di: estoy bien.",
                SpokenText.helpQuestion(java.util.Arrays.asList("Carlos", "Lucía")));
        assertEquals("Earcaster: María dijo \"me caí\" a las 17:10. Llama para saber cómo está.",
                SpokenText.helpMessage("María", "me caí", at(17, 10)));
        assertEquals("Earcaster: Alguien dijo \"ayuda\" a las 9:05. Llama para saber cómo está.",
                SpokenText.helpMessage("", "ayuda", at(9, 5)));
    }

    @Test
    public void cancelarElAviso() {
        // Criterion 2: «estoy bien» (I'm fine) within 10 seconds cancels.
        assertTrue(SpokenText.cancelsHelp("estoy bien", false));
        assertTrue(SpokenText.cancelsHelp("no no estoy bien gracias", false));
        assertTrue(SpokenText.cancelsHelp("no", true));
        // A lone, hesitant «no», or one inside another sentence, does not cancel.
        assertFalse(SpokenText.cancelsHelp("no", false));
        assertFalse(SpokenText.cancelsHelp("no me puedo levantar", true));
        assertFalse(SpokenText.cancelsHelp("ayuda", true));
    }
}
