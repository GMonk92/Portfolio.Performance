package name.abuchen.portfolio.piano;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import name.abuchen.portfolio.PortfolioLog;
import name.abuchen.portfolio.model.Client;

/**
 * Legge e salva il piano di investimento dentro il file di Portfolio
 * Performance. Il piano viene memorizzato in formato JSON come proprietà del
 * client: in questo modo il formato del file non cambia e il file resta
 * leggibile anche dalla versione originale di Portfolio Performance (che
 * semplicemente ignora la proprietà).
 */
public final class PianoStore
{
    /* package */ static final String PROPERTY_KEY = "piano-investimento"; //$NON-NLS-1$

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private PianoStore()
    {
    }

    public static PianoInvestimento load(Client client)
    {
        var json = client.getProperty(PROPERTY_KEY);
        if (json == null || json.isBlank())
            return new PianoInvestimento();

        try
        {
            var piano = GSON.fromJson(json, PianoInvestimento.class);
            return piano != null ? piano : new PianoInvestimento();
        }
        catch (JsonParseException e)
        {
            PortfolioLog.error(e);
            return new PianoInvestimento();
        }
    }

    public static void save(Client client, PianoInvestimento piano)
    {
        var json = GSON.toJson(piano);
        if (json.equals(client.getProperty(PROPERTY_KEY)))
            return;

        client.setProperty(PROPERTY_KEY, json);
        client.markDirty();
    }
}
