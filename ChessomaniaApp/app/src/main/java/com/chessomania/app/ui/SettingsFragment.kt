package com.chessomania.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import androidx.fragment.app.Fragment
import com.chessomania.app.R
import com.chessomania.app.SettingsManager

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        // 1. Sound Switch (Master On/Off)
        val switchSound = view.findViewById<SwitchCompat>(R.id.switch_sound)
        switchSound.isChecked = SettingsManager.isSoundEnabled(context)
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setSoundEnabled(context, isChecked)
            if (isChecked) {
                SettingsManager.playSound(context, "Move")
            }
        }

        // 2. Board Theme Spinner
        val boardSpinner = view.findViewById<Spinner>(R.id.spinner_board_theme)
        val boardThemes = arrayOf(
            "Classic", "Ocean", "Tournament", "Charcoal", "Ice", "Purple (Solid)", "Cherry", "Wood (Solid)", "Forest", "Midnight",
            "Blue Marble", "Blue", "Blue 2", "Blue 3", "Brown", "Canvas", "Green Plastic", "Green", "Grey", "Horsey", "IC",
            "Leather", "Maple", "Maple 2", "Marble", "Metal", "Olive", "Pink Pyramid", "Purple Diag", "Purple", "Wood",
            "Wood 2", "Wood 3", "Wood 4"
        )
        val boardAdapter = ArrayAdapter(context, R.layout.spinner_item, boardThemes)
        boardAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        boardSpinner.adapter = boardAdapter

        // Set initial selection
        val currentBoard = SettingsManager.getBoardTheme(context)
        val boardIndex = boardThemes.indexOf(currentBoard)
        if (boardIndex >= 0) {
            boardSpinner.setSelection(boardIndex)
        }

        boardSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = boardThemes[position]
                if (selected != SettingsManager.getBoardTheme(context)) {
                    SettingsManager.setBoardTheme(context, selected)
                    SettingsManager.playSound(context, "Move")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Piece Theme Spinner
        val pieceSpinner = view.findViewById<Spinner>(R.id.spinner_piece_theme)
        val pieceThemesDisplay = arrayOf(
            "CBurnett (Default)", "Merida", "Alpha", "California", "Cardinal", "Celtic", 
            "Chessnut", "Companion", "Governor", "Horsey", "Kosal", "Leipzig", "Maestro", 
            "Monarchy", "Mono", "Pixel", "Spatial", "Staunty", "Tatiana", "Totoy", 
            "Glass", "Metal", "Wood", "Unicode"
        )
        val pieceThemesInternal = arrayOf(
            "cburnett", "merida", "alpha", "california", "cardinal", "celtic", 
            "chessnut", "companion", "governor", "horsey", "kosal", "leipzig", "maestro", 
            "monarchy", "mono", "pixel", "spatial", "staunty", "tatiana", "totoy", 
            "Glass", "Metal", "Wood", "Unicode"
        )
        val pieceAdapter = ArrayAdapter(context, R.layout.spinner_item, pieceThemesDisplay)
        pieceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        pieceSpinner.adapter = pieceAdapter

        // Set initial selection
        val currentPiece = SettingsManager.getPieceTheme(context)
        val pieceIndex = pieceThemesInternal.indexOf(currentPiece)
        if (pieceIndex >= 0) {
            pieceSpinner.setSelection(pieceIndex)
        }

        pieceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedInternal = pieceThemesInternal[position]
                if (selectedInternal != SettingsManager.getPieceTheme(context)) {
                    SettingsManager.setPieceTheme(context, selectedInternal)
                    SettingsManager.playSound(context, "Move")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. Sound Theme Spinner
        val soundThemeSpinner = view.findViewById<Spinner>(R.id.spinner_sound_theme)
        val soundThemesDisplay = arrayOf("Standard", "Piano", "NES", "Robot", "Lisp", "Futuristic", "Woodland", "Instrument", "Silent")
        val soundThemesInternal = arrayOf("standard", "piano", "nes", "robot", "lisp", "futuristic", "woodland", "instrument", "Silent")
        val soundAdapter = ArrayAdapter(context, R.layout.spinner_item, soundThemesDisplay)
        soundAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        soundThemeSpinner.adapter = soundAdapter

        // Set initial selection
        val currentSoundTheme = SettingsManager.getSoundTheme(context)
        val soundIndex = soundThemesInternal.indexOf(currentSoundTheme)
        if (soundIndex >= 0) {
            soundThemeSpinner.setSelection(soundIndex)
        }

        soundThemeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedInternal = soundThemesInternal[position]
                if (selectedInternal != SettingsManager.getSoundTheme(context)) {
                    SettingsManager.setSoundTheme(context, selectedInternal)
                    SettingsManager.playSound(context, "Move")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 5. Server URL
        val textServerUrl = view.findViewById<TextView>(R.id.text_server_url)
        val btnShareUrl = view.findViewById<android.widget.Button>(R.id.btn_share_url)
        val btnPasteUrl = view.findViewById<android.widget.Button>(R.id.btn_paste_url)
        val btnResetUrl = view.findViewById<android.widget.Button>(R.id.btn_reset_url)

        fun updateUrlText() {
            textServerUrl.text = SettingsManager.getServerUrl(context)
        }
        updateUrlText()

        btnShareUrl.setOnClickListener {
            val url = SettingsManager.getServerUrl(context)
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Multiplayer Server URL")
            context.startActivity(shareIntent)
        }

        btnPasteUrl.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (pastedText.startsWith("http://") || pastedText.startsWith("https://")) {
                    SettingsManager.setServerUrl(context, pastedText)
                    updateUrlText()
                    Toast.makeText(context, "Server URL updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid server URL pasted", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        btnResetUrl.setOnClickListener {
            val defaultUrl = SettingsManager.getDefaultServerUrl(requireContext())
            SettingsManager.setServerUrl(context, defaultUrl)
            updateUrlText()
            Toast.makeText(context, "Server URL reset to default!", Toast.LENGTH_SHORT).show()
        }
    }
}
