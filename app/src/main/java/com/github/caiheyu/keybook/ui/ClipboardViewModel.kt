package com.github.caiheyu.keybook.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.github.caiheyu.keybook.core.clipboard.SensitiveClipboard

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    val clipboard: SensitiveClipboard,
) : ViewModel()
