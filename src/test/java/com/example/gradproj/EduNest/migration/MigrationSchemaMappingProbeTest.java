package com.example.gradproj.EduNest.migration;

import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EB-82: Migration Schema and Text Mapping Probe")
class MigrationSchemaMappingProbeTest {

    @Test
    @DisplayName("Verify that none of the 5 text columns use @Lob and all specify TEXT columnDefinition")
    void verifyNoLobOnTextColumns() throws NoSuchFieldException {
        // 1. ContactMessageEntity.message
        Field msgField = ContactMessageEntity.class.getDeclaredField("message");
        assertThat(msgField.isAnnotationPresent(Lob.class))
                .as("ContactMessageEntity.message should not use @Lob")
                .isFalse();
        Column msgCol = msgField.getAnnotation(Column.class);
        assertThat(msgCol).isNotNull();
        assertThat(msgCol.columnDefinition()).isEqualTo("TEXT");

        // 2. Project.brief (project_brief)
        Field briefField = Project.class.getDeclaredField("brief");
        assertThat(briefField.isAnnotationPresent(Lob.class))
                .as("Project.brief should not use @Lob")
                .isFalse();
        Column briefCol = briefField.getAnnotation(Column.class);
        assertThat(briefCol).isNotNull();
        assertThat(briefCol.name()).isEqualTo("project_brief");
        assertThat(briefCol.columnDefinition()).isEqualTo("TEXT");

        // 3. ProjectSubmission.feedBack (project_feedback)
        Field psFeedBack = ProjectSubmission.class.getDeclaredField("feedBack");
        assertThat(psFeedBack.isAnnotationPresent(Lob.class))
                .as("ProjectSubmission.feedBack should not use @Lob")
                .isFalse();
        Column psCol = psFeedBack.getAnnotation(Column.class);
        assertThat(psCol).isNotNull();
        assertThat(psCol.name()).isEqualTo("project_feedback");
        assertThat(psCol.columnDefinition()).isEqualTo("TEXT");

        // 4. Task.description (task_description)
        Field taskDescField = Task.class.getDeclaredField("description");
        assertThat(taskDescField.isAnnotationPresent(Lob.class))
                .as("Task.description should not use @Lob")
                .isFalse();
        Column taskCol = taskDescField.getAnnotation(Column.class);
        assertThat(taskCol).isNotNull();
        assertThat(taskCol.name()).isEqualTo("task_description");
        assertThat(taskCol.columnDefinition()).isEqualTo("TEXT");

        // 5. TaskSubmission.feedBack (task_feedback)
        Field tsFeedBack = TaskSubmission.class.getDeclaredField("feedBack");
        assertThat(tsFeedBack.isAnnotationPresent(Lob.class))
                .as("TaskSubmission.feedBack should not use @Lob")
                .isFalse();
        Column tsCol = tsFeedBack.getAnnotation(Column.class);
        assertThat(tsCol).isNotNull();
        assertThat(tsCol.name()).isEqualTo("task_feedback");
        assertThat(tsCol.columnDefinition()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("Verify legacy user_settings entity does not exist in com.example.gradproj.EduNest.entity")
    void verifyNoUserSettingsEntity() {
        boolean classFound = true;
        try {
            Class.forName("com.example.gradproj.EduNest.entity.UserSettings");
        } catch (ClassNotFoundException e) {
            classFound = false;
        }
        assertThat(classFound).as("UserSettings entity must not exist").isFalse();
    }
}
