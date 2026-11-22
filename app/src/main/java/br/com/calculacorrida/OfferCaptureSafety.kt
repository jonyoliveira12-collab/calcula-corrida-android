package br.com.calculacorrida

internal data class CaptureArea(val left:Int,val top:Int,val right:Int,val bottom:Int) {
    fun contains(other:CaptureArea)=other.left<other.right && other.top<other.bottom && left<=other.left && top<=other.top && right>=other.right && bottom>=other.bottom
    fun overlaps(other:CaptureArea)=left<right && top<bottom && other.left<other.right && other.top<other.bottom && left<other.right && right>other.left && top<other.bottom && bottom>other.top
}
internal data class CaptureSurface(val id:Int,val owner:String?,val area:CaptureArea,val layer:Int,val kind:Int,val display:Int=0)

/** Display fallback is allowed only for an unchanged, unobscured driver window. */
internal object OfferCaptureSafety {
    /** Keep the largest rectangular part containing no visible foreign pixels. */
    fun uncoveredArea(target:CaptureSurface,surfaces:List<CaptureSurface>,readerOwner:String,hiddenReaderAreas:Set<CaptureArea>,strictBackgroundOwnership:Boolean=true):CaptureArea? {
        if(target.display!=0 || target.owner==null || DriverPackages.name(target.owner)==null)return null
        if(surfaces.none{it.id==target.id && it.owner==target.owner && it.display==target.display && it.area.contains(target.area)})return null
        var area=target.area
        for(other in surfaces) {
            if(other.id==target.id || other.display!=target.display || !other.area.overlaps(area))continue
            if(other.layer<=target.layer && (!strictBackgroundOwnership || other.owner==target.owner))continue
            if(other.owner==readerOwner && other.kind==3 && other.area in hiddenReaderAreas)continue
            val cover=other.area
            area=listOf(CaptureArea(area.left,area.top,area.right,minOf(area.bottom,cover.top)),
                CaptureArea(area.left,maxOf(area.top,cover.bottom),area.right,area.bottom),
                CaptureArea(area.left,area.top,minOf(area.right,cover.left),area.bottom),
                CaptureArea(maxOf(area.left,cover.right),area.top,area.right,area.bottom))
                .filter{it.left<it.right && it.top<it.bottom}
                .maxByOrNull{(it.right-it.left).toLong()*(it.bottom-it.top)} ?: return null
        }
        return area
    }
    fun allows(target:CaptureSurface,surfaces:List<CaptureSurface>,readerOwner:String,hiddenReaderAreas:Set<CaptureArea>,strictBackgroundOwnership:Boolean=true):Boolean {
        if(target.display!=0 || target.owner==null || DriverPackages.name(target.owner)==null)return false
        if(surfaces.none{it.id==target.id && it.owner==target.owner && it.display==target.display && it.area.contains(target.area)})return false
        return surfaces.none { other ->
            // Rounded/transparent offer edges may expose a lower window too.
            // Only the same driver's background may sit behind the crop.
            other.id!=target.id && other.display==target.display && other.area.overlaps(target.area) &&
                (other.layer>target.layer || (strictBackgroundOwnership && other.owner!=target.owner)) &&
                !(other.owner==readerOwner && other.kind==3 && other.area in hiddenReaderAreas)
        }
    }
}
