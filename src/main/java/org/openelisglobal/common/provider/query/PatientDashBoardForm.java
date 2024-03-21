package org.openelisglobal.common.provider.query;

import org.openelisglobal.common.form.IPagingForm;
import org.openelisglobal.common.paging.PagingBean;
import org.openelisglobal.common.rest.provider.bean.homedashboard.OrderDisplayBean;

import java.util.List;

public class PatientDashBoardForm implements IPagingForm {

    private PagingBean paging;

    private List<OrderDisplayBean> displayItems;

    public void setOrderDisplayBeans(List<OrderDisplayBean> displayItems) {
        this.displayItems = displayItems;
    }

    public List<OrderDisplayBean> getDisplayItems() {
        return displayItems;
    }

    @Override
    public void setPaging(PagingBean paging) {
        this.paging = paging;
    }

    @Override
    public PagingBean getPaging() {
        return paging;
    }

}  