import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class test_time {
    public static void main(String[] args) {
        String dateStr = "2026-10-02T04:11:00.123456+00:00";
        if (dateStr.length() > 19) dateStr = dateStr.substring(0, 19);
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(dateStr);
            SimpleDateFormat out = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            out.setTimeZone(java.util.TimeZone.getDefault());
            System.out.println(out.format(date));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }
}
