package app.oido;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ProfileTest {

    @Test
    public void roundTrip() {
        Profile p = new Profile();
        p.name = "María";
        p.callMe = "Mari";
        p.sensitive = "Si pregunta por Jorge, dile que la quería mucho.";
        p.calls.add(new Profile.Call("Carlos", "+56 9 1234 5678", "su hijo"));
        p.light = true;
        p.doses.add("pastilla de la mañana");
        Profile back = Profile.fromMap(p.toMap());
        assertEquals("Mari", back.callMe);
        assertEquals(p.sensitive, back.sensitive);
        assertEquals(1, back.calls.size());
        assertEquals("su hijo", back.calls.get(0).relation);
        assertEquals("+56 9 1234 5678", back.calls.get(0).number);
        assertTrue(back.light);
        assertFalse(back.help);
        assertEquals(Arrays.asList("pastilla de la mañana"), back.doses);
    }

    @Test
    public void startsEmptyAndOff() {
        Profile p = Profile.fromMap(null);
        assertTrue(p.isEmpty());
        assertFalse(p.light);
        assertFalse(p.help);
    }

    @Test
    public void countsTextsNamesAndRelationsButNotNumbers() {
        Profile p = new Profile();
        p.name = "Ana";
        p.free = "12345";
        p.calls.add(new Profile.Call("Luis", "+56912345678", "hijo"));
        assertEquals(3 + 5 + 4 + 4, p.length());
        p.free = new String(new char[Profile.MAX_CHARS]).replace('\0', 'a');
        assertTrue(p.tooLong());
    }

    @Test
    public void ignoresWrongTypesAndContactsWithoutNumber() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", 42);
        m.put("light", "sí");
        List<Object> calls = new ArrayList<>();
        Map<String, Object> noNumber = new HashMap<>();
        noNumber.put("name", "Carlos");
        calls.add(noNumber);
        calls.add("basura");
        m.put("calls", calls);
        m.put("doses", Arrays.asList("a", "a", "", 3));
        Profile p = Profile.fromMap(m);
        assertEquals("", p.name);
        assertFalse(p.light);
        assertTrue(p.calls.isEmpty());
        assertEquals(Arrays.asList("a"), p.doses);
    }

    @Test
    public void familyUpdateReplacesOrAdds() {
        Profile p = new Profile();
        p.family = "Su hija Lucía vive en Barranquilla. Su hijo Andrés vive en Bogotá.";
        p.apply("family", "Su hijo Andrés vive en Bogotá.", "Su hijo Andrés vive en Medellín.");
        assertEquals("Su hija Lucía vive en Barranquilla. Su hijo Andrés vive en Medellín.", p.family);
        // An old sentence that is no longer there: the new one is added at the end.
        p.apply("family", "Su hijo vive en Cali.", "Su nieto Mateo vive con Lucía.");
        assertTrue(p.family.endsWith("Medellín. Su nieto Mateo vive con Lucía."));
        p.apply("likes", "", "Le gusta la cumbia.");
        assertEquals("Le gusta la cumbia.", p.likes);
        p.apply("other", "", "Usa audífono.");
        assertEquals("Usa audífono.", p.free);
    }
}
