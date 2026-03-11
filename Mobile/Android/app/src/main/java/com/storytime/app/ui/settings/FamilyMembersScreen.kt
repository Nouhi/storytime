package com.storytime.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.storytime.app.R
import com.storytime.app.model.FamilyMemberResponse
import com.storytime.app.network.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMembersScreen(
    onBack: () -> Unit,
    viewModel: FamilyMembersViewModel = viewModel()
) {
    val members by viewModel.members.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<FamilyMemberResponse?>(null) }
    var memberToDelete by remember { mutableStateOf<FamilyMemberResponse?>(null) }

    LaunchedEffect(Unit) { viewModel.loadMembers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_family_members)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.family_add_title))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (members.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.family_empty_title), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.family_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(members, key = { it.id }) { member ->
                    MemberCard(
                        member = member,
                        onEdit = { editingMember = member },
                        onDelete = { memberToDelete = member }
                    )
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        MemberDialog(
            title = stringResource(R.string.family_add_title),
            onDismiss = { showAddDialog = false },
            onSave = { name, role, description ->
                viewModel.addMember(name, role, description)
                showAddDialog = false
            }
        )
    }

    // Edit dialog
    editingMember?.let { member ->
        MemberDialog(
            title = stringResource(R.string.family_edit_title),
            initialName = member.name,
            initialRole = member.role,
            initialDescription = member.description ?: "",
            memberId = member.id,
            onDismiss = { editingMember = null },
            onSave = { name, role, description ->
                viewModel.updateMember(member.id, name, role, description)
                editingMember = null
            },
            onPhotoUpload = { uri ->
                viewModel.uploadMemberPhoto(member.id, uri)
            }
        )
    }

    // Delete confirmation
    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text(stringResource(R.string.family_delete_title)) },
            text = { Text(stringResource(R.string.family_delete_message, member.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMember(member.id)
                        memberToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.button_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }
}

@Composable
private fun localizedRoleName(role: String): String {
    return when (role) {
        "mom" -> stringResource(R.string.role_mom)
        "dad" -> stringResource(R.string.role_dad)
        "brother" -> stringResource(R.string.role_brother)
        "sister" -> stringResource(R.string.role_sister)
        "grandma" -> stringResource(R.string.role_grandma)
        "grandpa" -> stringResource(R.string.role_grandpa)
        "aunt" -> stringResource(R.string.role_aunt)
        "uncle" -> stringResource(R.string.role_uncle)
        "pet" -> stringResource(R.string.role_pet)
        "friend" -> stringResource(R.string.role_friend)
        "companion" -> stringResource(R.string.role_companion)
        "classmate" -> stringResource(R.string.role_classmate)
        "neighbor" -> stringResource(R.string.role_neighbor)
        "magical-friend" -> stringResource(R.string.role_magical_friend)
        "other" -> stringResource(R.string.role_other)
        else -> role.replace("-", " ").replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun MemberCard(
    member: FamilyMemberResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (member.photoPath != null) {
                AsyncImage(
                    model = ApiClient.imageUrl("/api/photos/${member.photoPath}"),
                    contentDescription = member.name,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            FamilyMembersViewModel.roleEmoji(member.role),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    localizedRoleName(member.role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!member.description.isNullOrBlank()) {
                    Text(
                        member.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.button_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberDialog(
    title: String,
    initialName: String = "",
    initialRole: String = "brother",
    initialDescription: String = "",
    memberId: Int? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, description: String?) -> Unit,
    onPhotoUpload: ((Uri) -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var role by remember { mutableStateOf(initialRole) }
    var description by remember { mutableStateOf(initialDescription) }
    var expanded by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPhotoUpload?.invoke(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = "${FamilyMembersViewModel.roleEmoji(role)} ${localizedRoleName(role)}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.family_section_details)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        FamilyMembersViewModel.ROLES.forEach { (value, emoji) ->
                            val roleName = localizedRoleName(value)
                            DropdownMenuItem(
                                text = { Text("$emoji $roleName") },
                                onClick = {
                                    role = value
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.family_section_description)) },
                    placeholder = { Text(stringResource(R.string.family_description_placeholder)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 4
                )

                if (onPhotoUpload != null) {
                    OutlinedButton(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.family_upload_photo))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, role, description.ifBlank { null }) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.button_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}
