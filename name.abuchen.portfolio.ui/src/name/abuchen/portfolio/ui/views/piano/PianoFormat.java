package name.abuchen.portfolio.ui.views.piano;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;

/**
 * Formattazione e lettura dei numeri nelle sezioni del piano di investimento.
 * Le percentuali si inseriscono come numeri percentuali ("1,9" = 1,9%).
 */
/* package */ final class PianoFormat
{
    private static final Locale LOCALE = Locale.ITALY;
    private static final String EMPTY = ""; //$NON-NLS-1$

    private PianoFormat()
    {
    }

    private static DecimalFormat format(String pattern)
    {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(LOCALE));
    }

    public static String euro(Double value)
    {
        if (value == null)
            return EMPTY;
        return format("#,##0 €").format(Math.round(value)); //$NON-NLS-1$
    }

    public static String euro2(Double value)
    {
        if (value == null)
            return EMPTY;
        return format("#,##0.00 €").format(value); //$NON-NLS-1$
    }

    /** Numero senza simbolo, adatto a essere modificato. */
    public static String numero(Double value)
    {
        if (value == null)
            return EMPTY;
        return format("#,##0.##").format(value); //$NON-NLS-1$
    }

    public static String intero(int value)
    {
        return String.valueOf(value);
    }

    public static String percentuale(Double value)
    {
        if (value == null)
            return EMPTY;
        return format("0.0%").format(value); //$NON-NLS-1$
    }

    public static String percentuale2(Double value)
    {
        if (value == null)
            return EMPTY;
        return format("0.00%").format(value); //$NON-NLS-1$
    }

    /** Percentuale da modificare: 0,019 diventa "1,9". */
    public static String percentualeModifica(Double value)
    {
        if (value == null)
            return EMPTY;
        return format("0.###").format(value * 100); //$NON-NLS-1$
    }

    public static double fromMoney(Money money)
    {
        return money.getAmount() / Values.Amount.divider();
    }

    /**
     * Legge un numero scritto dall'utente. Accetta sia la virgola sia il punto
     * come separatore decimale e ignora simboli come € e %. Restituisce null
     * se il testo è vuoto.
     */
    public static Double leggi(String text) throws ParseException
    {
        return leggi(text, true);
    }

    private static Double leggi(String text, boolean puntoMigliaia) throws ParseException
    {
        if (text == null)
            return null;

        var t = text.replace("€", EMPTY).replace("%", EMPTY).replace(" ", EMPTY) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .replace(" ", EMPTY).trim(); //$NON-NLS-1$
        if (t.isEmpty())
            return null;

        int lastComma = t.lastIndexOf(',');
        int lastDot = t.lastIndexOf('.');

        String normalized;
        if (lastComma >= 0 && lastDot >= 0)
        {
            // il separatore che compare per ultimo è quello decimale
            if (lastComma > lastDot)
                normalized = t.replace(".", EMPTY).replace(',', '.'); //$NON-NLS-1$
            else
                normalized = t.replace(",", EMPTY); //$NON-NLS-1$
        }
        else if (lastComma >= 0)
        {
            normalized = t.replace(',', '.');
        }
        else if (lastDot >= 0 && t.indexOf('.') != lastDot)
        {
            // più punti: separatori delle migliaia (es. 1.000.000)
            normalized = t.replace(".", EMPTY); //$NON-NLS-1$
        }
        else if (puntoMigliaia && lastDot > 0 && t.length() - lastDot - 1 == 3)
        {
            // un solo punto seguito da tre cifre: migliaia (es. 35.000)
            normalized = t.replace(".", EMPTY); //$NON-NLS-1$
        }
        else
        {
            normalized = t;
        }

        var pos = new ParsePosition(0);
        var number = NumberFormat.getInstance(Locale.ROOT).parse(normalized, pos);
        if (number == null || pos.getIndex() != normalized.length())
            throw new ParseException(text, pos.getErrorIndex());

        return number.doubleValue();
    }

    /** Come {@link #leggi(String)} ma per le percentuali: "1,9" diventa 0,019. */
    public static Double leggiPercentuale(String text) throws ParseException
    {
        var value = leggi(text, false);
        return value == null ? null : value / 100;
    }
}
