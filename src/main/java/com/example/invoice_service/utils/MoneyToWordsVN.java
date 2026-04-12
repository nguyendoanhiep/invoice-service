package com.example.invoice_service.utils;

public class MoneyToWordsVN {

    private static final String[] DIGITS = {
            "không", "một", "hai", "ba", "bốn",
            "năm", "sáu", "bảy", "tám", "chín"
    };

    private static final String[] UNITS = {
            "", "nghìn", "triệu", "tỷ"
    };

    public static String toWords(long number) {
        if (number == 0) {
            return "Không";
        }

        StringBuilder result = new StringBuilder();
        int unitIndex = 0;

        while (number > 0) {
            int group = (int) (number % 1000);
            if (group != 0) {
                String groupText = readThreeDigits(group);
                result.insert(0, groupText + " " + UNITS[unitIndex] + " ");
            }
            number /= 1000;
            unitIndex++;
        }

        String text = result.toString().trim().replaceAll("\\s+", " ");
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private static String readThreeDigits(int number) {
        int hundred = number / 100;
        int ten = (number % 100) / 10;
        int unit = number % 10;

        StringBuilder sb = new StringBuilder();

        // Hàng trăm
        if (hundred > 0) {
            sb.append(DIGITS[hundred]).append(" trăm");
            if (ten == 0 && unit > 0) {
                sb.append(" lẻ");
            }
        }

        // Hàng chục & đơn vị
        if (ten > 1) {
            sb.append(" ").append(DIGITS[ten]).append(" mươi");

            if (unit == 1) sb.append(" mốt");
            else if (unit == 5) sb.append(" lăm");
            else if (unit > 0) sb.append(" ").append(DIGITS[unit]);

        } else if (ten == 1) {
            sb.append(" mười");

            if (unit == 5) sb.append(" lăm");
            else if (unit > 0) sb.append(" ").append(DIGITS[unit]);

        } else if (ten == 0 && unit > 0) {
            sb.append(" ").append(DIGITS[unit]); // 5 -> năm
        }

        return sb.toString().trim();
    }
}
