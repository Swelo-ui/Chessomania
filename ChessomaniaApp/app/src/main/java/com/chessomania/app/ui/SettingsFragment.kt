package com.chessomania.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Button
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

        // 1b. Haptic Feedback Switch
        val switchHaptic = view.findViewById<SwitchCompat>(R.id.switch_haptic)
        switchHaptic.isChecked = SettingsManager.isHapticEnabled(context)
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setHapticEnabled(context, isChecked)
            if (isChecked) {
                SettingsManager.performHapticFeedback(context, SettingsManager.HapticType.MOVE)
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

        val btnRuleBook = view.findViewById<Button>(R.id.btn_rule_book)
        btnRuleBook.setOnClickListener {
            showRuleBookDialog()
        }

        val editDebugCode = view.findViewById<android.widget.EditText>(R.id.edit_debug_code)
        val btnActivateDebug = view.findViewById<Button>(R.id.btn_activate_debug)
        btnActivateDebug.setOnClickListener {
            val code = editDebugCode.text.toString().trim()
            if (code.equals("HOSTHINT", ignoreCase = true)) {
                com.chessomania.app.net.SecurePrefs.setHostHintEnabled(context, true)
                Toast.makeText(context, "Debug mode activated", Toast.LENGTH_SHORT).show()
                editDebugCode.text.clear()
            } else {
                Toast.makeText(context, "Configuration updated", Toast.LENGTH_SHORT).show()
                editDebugCode.text.clear()
            }
        }
    }

    private fun showRuleBookDialog() {
        val context = context ?: return
        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_rule_book)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val btnClose = dialog.findViewById<Button>(R.id.btn_close_rules)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}
