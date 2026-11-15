package br.com.calculacorrida

import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class SeasonalThemeTest {
    private fun day(year:Int,month:Int,day:Int)=Calendar.getInstance().apply{clear();set(year,month,day,12,0)}
    @Test fun christmasAndNewYearAreIncludedButTheSeasonExpires() {
        assertTrue(AppAppearance.holidayAvailable(day(2026,Calendar.DECEMBER,25)))
        assertTrue(AppAppearance.holidayAvailable(day(2027,Calendar.JANUARY,1)))
        assertTrue(AppAppearance.holidayAvailable(day(2027,Calendar.JANUARY,6)))
        assertFalse(AppAppearance.holidayAvailable(day(2027,Calendar.JANUARY,7)))
        assertFalse(AppAppearance.holidayAvailable(day(2027,Calendar.FEBRUARY,1)))
    }
}
