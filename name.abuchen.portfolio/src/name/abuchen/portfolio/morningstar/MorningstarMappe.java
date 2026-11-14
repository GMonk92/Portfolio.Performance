package name.abuchen.portfolio.morningstar;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Tabelle di conversione dei codici Morningstar nei nomi delle categorie, prese
 * dallo script pp-portfolio-classifier (file mappe.json).
 */
public final class MorningstarMappe
{
    private static MorningstarMappe instance;

    private final Map<String, Object> dati;

    private MorningstarMappe(Map<String, Object> dati)
    {
        this.dati = dati;
    }

    public static synchronized MorningstarMappe get()
    {
        if (instance == null)
        {
            try (var in = new InputStreamReader(MorningstarMappe.class.getResourceAsStream("mappe.json"), //$NON-NLS-1$
                            StandardCharsets.UTF_8))
            {
                Map<String, Object> dati = new Gson().fromJson(in, new TypeToken<Map<String, Object>>()
                {
                }.getType());
                instance = new MorningstarMappe(dati);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> mappa(String nome)
    {
        var m = (Map<String, String>) dati.get(nome);
        return m != null ? Collections.unmodifiableMap(m) : Map.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> colori()
    {
        return Collections.unmodifiableList((List<String>) dati.get("COLORS")); //$NON-NLS-1$
    }

    public Map<String, String> stockStyle()
    {
        return mappa("map_stock_style_1"); //$NON-NLS-1$
    }

    public Map<String, String> stockSector()
    {
        return mappa("map_stock_sector_1"); //$NON-NLS-1$
    }

    /** Codici delle regioni Morningstar (fondi azionari). */
    public Map<String, String> regioni()
    {
        return mappa("map_region_1"); //$NON-NLS-1$
    }

    /** Codice del paese → regione (obbligazioni e azioni singole). */
    public Map<String, String> regioniDaPaese()
    {
        return mappa("map_region_2"); //$NON-NLS-1$
    }

    /** Codice del paese → nome del paese. */
    public Map<String, String> paesi()
    {
        return mappa("map_country_1"); //$NON-NLS-1$
    }
}
