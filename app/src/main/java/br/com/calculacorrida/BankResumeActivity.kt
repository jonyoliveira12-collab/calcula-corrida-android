package br.com.calculacorrida

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** A notification tap opens Android's real settings; activation always belongs to the user. */
class BankResumeActivity:Activity() {
    override fun onCreate(state:Bundle?) {
        AppAppearance.apply(this);super.onCreate(state)
        if(BankModeReminder.isReaderEnabled(this)) {
            BankModeReminder.clear(this)
            Toast.makeText(this,"A acessibilidade do JHONDRIVER já está ativada.",Toast.LENGTH_SHORT).show()
        } else BankModeReminder.openReaderSettings(this)
        finish()
    }
}
