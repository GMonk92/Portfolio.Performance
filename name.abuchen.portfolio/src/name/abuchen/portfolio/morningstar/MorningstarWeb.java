package name.abuchen.portfolio.morningstar;

import java.io.IOException;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import name.abuchen.portfolio.util.WebAccess;
import name.abuchen.portfolio.util.WebAccess.WebAccessException;

/**
 * Legge i dati dal sito di Morningstar, come fa lo script
 * pp-portfolio-classifier: prima prende il gettone di accesso dalla pagina
 * pubblica di Morningstar, poi chiama l'API "securities".
 */
@SuppressWarnings("nls")
public class MorningstarWeb implements MorningstarDati.Fonte
{
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/88.0.4324.150 Safari/537.36";
    private static final Pattern TOKEN = Pattern.compile("const maasToken\\s*=\\s*\"([^\"]+)\"");

    private final String dominio;
    private String token;

    /**
     * @param dominio
     *            dominio di Morningstar da cui prendere il gettone, per
     *            esempio "de" o "it"
     */
    public MorningstarWeb(String dominio)
    {
        this.dominio = dominio;
    }

    private String token() throws IOException
    {
        if (token == null)
        {
            var pagina = new WebAccess("www.morningstar." + dominio, "/Common/funds/snapshot/PortfolioSAL.aspx")
                            .addUserAgent(USER_AGENT).get();
            var m = TOKEN.matcher(pagina);
            if (!m.find())
                throw new IOException("Gettone di accesso non trovato su www.morningstar." + dominio);
            token = m.group(1);
        }
        return token;
    }

    @Override
    public JsonElement leggi(String isin, String viewId) throws IOException
    {
        try
        {
            var risposta = new WebAccess("www.emea-api.morningstar.com", "/ecint/v1/securities/" + isin)
                            .addParameter("idtype", "ISIN")
                            .addParameter("viewid", viewId)
                            .addParameter("currencyId", "EUR")
                            .addParameter("responseViewFormat", "json")
                            .addParameter("languageId", "en-UK")
                            .addBearer(token())
                            .get();
            return JsonParser.parseString(risposta);
        }
        catch (WebAccessException e)
        {
            if (e.getHttpErrorCode() == 401 || e.getHttpErrorCode() == 404)
                return null;
            throw e;
        }
    }
}
