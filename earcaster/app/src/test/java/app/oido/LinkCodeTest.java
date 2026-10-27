package app.oido;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class LinkCodeTest {

    @Test
    public void generaCodigosValidos() {
        Random r = new Random(7);
        for (int i = 0; i < 200; i++) {
            String code = LinkCode.generate(r);
            assertEquals(8, code.length());
            assertEquals(code, LinkCode.parse(code));
            assertTrue(code.matches("[2-9A-HJ-NP-Z]{8}"));
        }
    }

    @Test
    public void formatoConGuion() {
        assertEquals("K7P2-9QXM", LinkCode.format("K7P29QXM"));
    }

    @Test
    public void leeLoEscritoYElQr() {
        assertEquals("K7P29QXM", LinkCode.parse("k7p2-9qxm"));
        assertEquals("K7P29QXM", LinkCode.parse(" K7P2 9QXM "));
        assertEquals("K7P29QXM", LinkCode.parse("earcaster:K7P29QXM"));
    }

    @Test
    public void rechazaLoQueNoEsUnCodigo() {
        assertNull(LinkCode.parse(null));
        assertNull(LinkCode.parse("K7P29QX"));
        assertNull(LinkCode.parse("K7P29QX0"));
        assertNull(LinkCode.parse("https://example.com"));
    }
}
