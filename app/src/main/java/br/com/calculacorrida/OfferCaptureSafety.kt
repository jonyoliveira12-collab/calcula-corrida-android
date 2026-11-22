package br.com.calculacorrida

internal data class CaptureArea(val left:Int,val top:Int,val right:Int,val bottom:Int) {
    fun overlaps(other:CaptureArea)=left<other.right && right>other.left && top<other.bottom && bottom>other.top
}
internal data class CaptureSurface(val id:Int,val owner:String?,val area:CaptureArea,val layer:Int,val kind:Int)

/** Display fallback is allowed only for an unchanged, unobscured driver window. */
internal object OfferCaptureSafety {
    fun allows(target:CaptureSurface,surfaces:List<CaptureSurface>,readerOwner:String,hiddenReaderAreas:Set<CaptureArea>):Boolean {
        if(target.owner==null || DriverPackages.name(target.owner)==null)return false
        if(surfaces.none{it.id==target.id && it.owner==target.owner && it.area==target.area})return false
        return surfaces.none { other ->
            other.id!=target.id && other.layer>target.layer && other.area.overlaps(target.area) &&
                !(other.owner==readerOwner && other.kind==3 && other.area in hiddenReaderAreas)
        }
    }
}
