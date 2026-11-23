package com.omnia.backend.service.impl;

import com.omnia.backend.service.interfaces.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import com.omnia.backend.enums.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordUrl;

    /** Optional web page for resetting the password; empty = app link only. */
    @Value("${app.web.reset-password-url:}")
    private String webResetPasswordUrl;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private String webResetSection(
            String token
    ) {
        if (webResetPasswordUrl == null
                || webResetPasswordUrl.isBlank()) {
            return "";
        }

        String webLink =
                UriComponentsBuilder
                        .fromUriString(webResetPasswordUrl.trim())
                        .queryParam("token", token)
                        .encode()
                        .build()
                        .toUriString();

        return "\n\nNëse je në kompjuter, rivendose në faqen e Omnia:\n"
                + webLink;
    }

    @Override
    public void sendEmailVerification(
            String recipientEmail,
            String recipientName,
            String token
    ) {

        String verificationLink =
                backendBaseUrl
                        + "/api/auth/verify-email?token="
                        + token;

        String subject = "Verifiko email-in tënd në Omnia";

        String body =
                "Përshëndetje " + recipientName + ",\n\n"
                        + "Faleminderit që u regjistrove në Omnia.\n\n"
                        + "Për të konfirmuar adresën tënde të email-it, hap linkun më poshtë:\n\n"
                        + verificationLink
                        + "\n\nLinku skadon pas 24 orësh.\n\n"
                        + "Nëse nuk e krijove ti këtë llogari, mund ta shpërfillësh këtë email.\n\n"
                        + "Ekipi Omnia";

        sendSimpleEmail(
                recipientEmail,
                subject,
                body
        );
    }

    @Override
    public void sendPasswordResetEmail(
            String recipientEmail,
            String recipientName,
            String token
    ) {

        String passwordResetLink =
                resetPasswordUrl
                        + "?token="
                        + token;

        String subject = "Rivendos fjalëkalimin në Omnia";

        String body =
                "Përshëndetje " + recipientName + ",\n\n"
                        + "Kemi marrë një kërkesë për të rivendosur fjalëkalimin tënd në Omnia.\n\n"
                        + "Për të zgjedhur një fjalëkalim të ri, hap linkun më poshtë:\n\n"
                        + passwordResetLink
                        + webResetSection(token)
                        + "\n\nKodi për rivendosje manuale:\n"
                        + token
                        + "\n\nLinku skadon pas 30 minutash dhe mund të përdoret vetëm një herë.\n\n"
                        + "Nëse nuk e kërkove ti ndryshimin e fjalëkalimit, shpërfille këtë email.\n\n"
                        + "Ekipi Omnia";

        sendSimpleEmail(
                recipientEmail,
                subject,
                body
        );
    }
    @Override
    public void sendOrderStatusEmail(
            String recipientEmail,
            String recipientName,
            Long orderId,
            OrderStatus status,
            BigDecimal totalAmount
    ) {
        String subject;
        String statusMessage;

        switch (status) {
            case PENDING -> {
                subject =
                        "Porosia juaj Omnia u krijua";

                statusMessage =
                        "Porosia juaj u krijua me sukses "
                                + "dhe është në pritje të konfirmimit.";
            }

            case CONFIRMED -> {
                subject =
                        "Porosia juaj Omnia u konfirmua";

                statusMessage =
                        "Porosia juaj është konfirmuar.";
            }

            case PROCESSING -> {
                subject =
                        "Porosia juaj Omnia është në përpunim";

                statusMessage =
                        "Porosia juaj po përgatitet.";
            }

            case SHIPPED -> {
                subject =
                        "Porosia juaj Omnia është dërguar";

                statusMessage =
                        "Porosia juaj është nisur për dërgesë.";
            }

            case DELIVERED -> {
                subject =
                        "Porosia juaj Omnia u dorëzua";

                statusMessage =
                        "Porosia juaj është dorëzuar me sukses.";
            }

            case CANCELLED -> {
                subject =
                        "Porosia juaj Omnia u anulua";

                statusMessage =
                        "Porosia juaj është anuluar.";
            }

            default -> {
                subject =
                        "Statusi i porosisë suaj Omnia ndryshoi";

                statusMessage =
                        "Statusi i porosisë suaj është përditësuar.";
            }
        }

        String displayName =
                recipientName == null
                        || recipientName.trim().isEmpty()
                        ? "klient"
                        : recipientName.trim();

        String formattedTotal =
                totalAmount == null
                        ? "-"
                        : totalAmount
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                        .toPlainString()
                        + " €";

        String body =
                "Përshëndetje "
                        + displayName
                        + ",\n\n"
                        + statusMessage
                        + "\n\n"
                        + "Porosia: #"
                        + orderId
                        + "\n"
                        + "Statusi: "
                        + getOrderStatusLabel(status)
                        + "\n"
                        + "Totali: "
                        + formattedTotal
                        + "\n\n"
                        + "Faleminderit që zgjodhët Omnia.\n\n"
                        + "Ekipi Omnia";

        sendSimpleEmail(
                recipientEmail,
                subject,
                body
        );
    }
    private String getOrderStatusLabel(
            OrderStatus status
    ) {
        if (status == null) {
            return "-";
        }

        return switch (status) {
            case PENDING -> "Në pritje";
            case CONFIRMED -> "Konfirmuar";
            case PROCESSING -> "Në përpunim";
            case SHIPPED -> "Dërguar";
            case DELIVERED -> "E dorëzuar";
            case CANCELLED -> "E anuluar";
        };
    }

    @Override
    public void sendInquiryEmail(
            String recipientEmail,
            com.omnia.backend.event.InquiryCreatedEvent inquiry
    ) {
        boolean visit = "VISIT".equals(inquiry.type());
        String product = inquiry.productName() == null ? "" : inquiry.productName().strip();
        String subject = inquiryTitle(visit, product);

        StringBuilder body = new StringBuilder()
                .append("Përshëndetje")
                .append(inquiry.companyName() == null || inquiry.companyName().isBlank()
                        ? "" : " " + inquiry.companyName().strip())
                .append(",\n\n")
                .append(visit
                        ? "Një blerës kërkon të vizitojë pronën tuaj në Omnia.\n\n"
                        : "Një blerës kërkon t'ju kontaktojë për njoftimin tuaj në Omnia.\n\n")
                .append("Njoftimi: ").append(product)
                .append(" (#").append(inquiry.productId()).append(")\n")
                .append("Emri: ").append(inquiry.contactName()).append("\n")
                .append("Telefoni: ").append(inquiry.contactPhone()).append("\n");
        if (inquiry.visitAt() != null) {
            body.append("Orari i zgjedhur: ")
                    .append(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                            .format(inquiry.visitAt()))
                    .append("\n");
        } else if (inquiry.preferredDate() != null) {
            body.append("Data e dëshiruar: ")
                    .append(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                            .format(inquiry.preferredDate()))
                    .append("\n");
        }
        if (inquiry.message() != null && !inquiry.message().isBlank()) {
            body.append("Mesazhi: ").append(inquiry.message().strip()).append("\n");
        }
        body.append("\nKërkesën e gjeni te paneli i kompanisë → Kërkesat e blerësve, ")
                .append("ku mund t'i ndryshoni edhe statusin.\n\n")
                .append("Ekipi Omnia");

        sendSimpleEmail(recipientEmail, subject, body.toString());
    }

    /** "Kërkesë e re për vizitë: <titulli>" / "Kërkesë e re për kontakt: <titulli>". */
    static String inquiryTitle(boolean visit, String productName) {
        String title = (visit ? "Kërkesë e re për vizitë: " : "Kërkesë e re për kontakt: ")
                + (productName == null ? "" : productName.strip());
        return title.length() > 150 ? title.substring(0, 149) + "…" : title;
    }

    private void sendSimpleEmail(
            String recipientEmail,
            String subject,
            String body
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}