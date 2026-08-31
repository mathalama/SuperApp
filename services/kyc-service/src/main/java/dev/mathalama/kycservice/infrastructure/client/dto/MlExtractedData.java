package dev.mathalama.kycservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlExtractedData {
    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("document_number")
    private String documentNumber;

    @JsonProperty("date_of_birth")
    private LocalDate dateOfBirth;

    @JsonProperty("expiry_date")
    private LocalDate expiryDate;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("nationality")
    private String nationality;
}
