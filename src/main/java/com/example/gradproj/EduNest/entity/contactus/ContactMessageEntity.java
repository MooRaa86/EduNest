package com.example.gradproj.EduNest.entity.contactus;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "contact_message")
public class ContactMessageEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_message_id")
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(name = "name")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+)\\.(com|net|org|edu|ac\\.[a-z]{2,3})$",
            message = "Invalid email format.")
    @Column(name = "email")
    private String email;


    @Pattern(regexp = "^01[0-9]{9}$", message = "Invalid phone number")
    @NotBlank(message = "Phone is required")
    @Column(name = "phone")
    private String phone;

    @NotBlank(message = "Message is required")
    @Lob
    @Column(name = "message")
    private String message;
}
