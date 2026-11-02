package com.example.meetingbooking.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record FreeSlotRequest(

        @NotNull(message = "Date is required")
        LocalDate date,

        @Min(value = 1, message = "Minimum duration must be at least 1 minute")
        @Max(value = 540, message = "Minimum duration cannot exceed 540 minutes")
        int minimumDurationMinutes,

        @NotNull(message = "Attendee emails are required")
        List<@NotBlank @Email String> attendeeEmails
) {
}