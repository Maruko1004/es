package com.hcf.nszh.provider.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hcf.nszh.common.enums.ErrorEnum;
import com.hcf.nszh.common.exception.BusinessException;
import com.hcf.nszh.common.exception.ServiceException;
import com.hcf.nszh.common.security.shiro.utils.UserUtils;
import com.hcf.nszh.common.utils.StringUtils;
import com.hcf.nszh.common.utils.UUIDUtils;
import com.hcf.nszh.provider.system.api.dto.GetOfficeTreeDTO;
import com.hcf.nszh.provider.system.api.dto.OfficeIdDTO;
import com.hcf.nszh.provider.system.api.dto.OperatorOfficeDTO;
import com.hcf.nszh.provider.system.api.dto.SearchOfficeDTO;
import com.hcf.nszh.provider.system.api.vo.*;
import com.hcf.nszh.provider.system.entity.AreaEntity;
import com.hcf.nszh.provider.system.entity.OfficeEntity;
import com.hcf.nszh.provider.system.entity.UserEntity;
import com.hcf.nszh.provider.system.mapper.AreaMapper;
import com.hcf.nszh.provider.system.mapper.OfficeMapper;
import com.hcf.nszh.provider.system.mapper.UserMapper;
import com.hcf.nszh.provider.system.service.OfficeService;
import com.hcf.nszh.provider.system.utils.ObjConvert;
import com.hcf.nszh.provider.system.utils.OperatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.dozer.DozerBeanMapper;
import org.python.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Furant
 * 2019/7/22 11:05
 */

@Service
@Slf4j
public class OfficeServiceImpl implements OfficeService {

    @Autowired
    private OfficeMapper officeMapper;

    @Autowired
    private DozerBeanMapper dozerBeanMapper;

    @Autowired
    private AreaMapper areaMapper;

    @Autowired
    private UserMapper userMapper;


    @Override
    public List<OfficeVo> listOffice() {
        List<OfficeVo> officeVos = officeMapper.listRootOffice();
        if (CollectionUtils.isEmpty(officeVos)) {
            return new ArrayList<>(0);
        }
        return officeVos;
    }

    @Override
    public OfficeVo getOfficeById(OfficeIdDTO officeIdDTO) {

        if (StringUtils.isBlank(officeIdDTO.getOfficeId())) {
            throw new BusinessException(ErrorEnum.PARAMS_WRONG);
        }

        OfficeVo officeById = officeMapper.getOfficeById(officeIdDTO.getOfficeId());
        return officeById;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT,
            timeout = 36000, rollbackFor = Exception.class)
    @Override
    public String deleteOfficeById(OfficeIdDTO officeIdDTO) {

        if (StringUtils.isBlank(officeIdDTO.getOfficeId())) {
            throw new BusinessException(ErrorEnum.PARAMS_WRONG);
        }


        List<UserEntity> userEntities = userMapper.listUserByOfficeId(officeIdDTO.getOfficeId());
        if (CollectionUtils.isNotEmpty(userEntities)) {
            throw new BusinessException(ErrorEnum.OFFICE_CANNOT_DELETE_IN_USE);
        }
        //????????????????????????????????????
        List<OfficeVo> officeByParentId = officeMapper.getOfficeByParentId(officeIdDTO.getOfficeId());
        if (officeByParentId != null && officeByParentId.size() > 0) {
            throw new BusinessException(ErrorEnum.CHILD_EXIST_CANNOT_DELETE);
        }
        officeMapper.deleteById(officeIdDTO.getOfficeId());

        return "success";
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT,
            timeout = 36000, rollbackFor = Exception.class)
    @Override
    public OfficeDetailVO operatorOffice(OperatorOfficeDTO operatorOfficeDTO) {

        //?????? NPE
        if (operatorOfficeDTO == null) {
            throw new ServiceException();
        }

        if (StringUtils.isBlank(operatorOfficeDTO.getParentId())) {
            throw new BusinessException(ErrorEnum.SELECT_PARENT_OFFICE);
        }

        OfficeEntity officeEntity = new OfficeEntity();
        OfficeDetailVO officeDetailVO = new OfficeDetailVO();
        ObjConvert.copyProperties(operatorOfficeDTO, officeDetailVO);
        // ?????????????????????
        if (StringUtils.isNotBlank(officeDetailVO.getParentId()) && !"0".equals(operatorOfficeDTO.getParentId())) {
            OfficeDetailVO officeDetailVO1 = new OperatorUtils().getOffice(officeDetailVO.getParentId());
            if (officeDetailVO1 != null) {
                officeDetailVO.setParent(officeDetailVO1);
            } else {
                throw new BusinessException(ErrorEnum.OFFICE_NOT_EXIST);
            }
        }

        // ????????????????????????
        if (null != officeDetailVO.getParent()) {
            if ("0".equals(officeDetailVO.getParent().getParentIds()) || StringUtils.isBlank(officeDetailVO.getParent().getParentIds())) {
                officeDetailVO.setParentIds(officeDetailVO.getParent().getId());
            } else {
                officeDetailVO.setParentIds(officeDetailVO.getParent().getParentIds() + "," + officeDetailVO.getParent().getId());
            }

        } else {
            officeDetailVO.setParentIds("0");
        }

        String id = "0";
        if (StringUtils.isNotBlank(officeDetailVO.getId())) {
            id = officeDetailVO.getId();
        }
        OfficeVo OfficeVo1 = officeMapper.getOfficeById(id);


        UserVo user = UserUtils.getUser();
        Long userId = user.getUserId();
        //???????????????????????????????????????????????????????????????
        List<OfficeVo> officeVo = officeMapper.getOfficeIsExistByName(operatorOfficeDTO.getName(), operatorOfficeDTO.getParentId(), operatorOfficeDTO.getId(), null);
        if (officeVo != null && officeVo.size() > 0) {
            throw new BusinessException(ErrorEnum.OFFICE_EXIST);
        }

        // ?????????????????????
        if (StringUtils.isBlank(operatorOfficeDTO.getId())) {
            officeDetailVO.setId(UUIDUtils.uuid());
            officeDetailVO.setCreateBy(String.valueOf(userId));
            officeDetailVO.setCreateDate(new Date());
            officeDetailVO.setUpdateDate(new Date());
            officeDetailVO.setUpdateBy(String.valueOf(userId));
            officeDetailVO.setParentId(officeDetailVO.getParentId());

            if (StringUtils.isNotBlank(operatorOfficeDTO.getAreaId())) {
                AreaEntity areaEntity = areaMapper.get(operatorOfficeDTO.getAreaId());
                AreaDetailVO areaDetailVO = ObjConvert.newInstance(areaEntity, AreaDetailVO.class);
                officeDetailVO.setAreaId(operatorOfficeDTO.getAreaId());
                officeDetailVO.setArea(areaDetailVO);
            }
            if (officeDetailVO.getSort() == null) {
                //????????????????????????1
                officeDetailVO.setSort(new BigDecimal(1));
            }
            if (StringUtils.isBlank(officeDetailVO.getType())) {
                //??????????????????????????????1
                officeDetailVO.setType("1");
            }

            dozerBeanMapper.map(officeDetailVO, officeEntity);
            System.out.println(officeEntity);
            if (StringUtils.isEmpty(officeEntity.getDelFlag())) {
                officeEntity.setDelFlag("0");
            }
            try {
                officeMapper.insert(officeEntity);
            } catch (Exception e) {
                log.error("????????????{}", e);
                return null;
            }


            return officeDetailVO;
        } else {
            officeDetailVO.setUpdateDate(new Date());
            officeDetailVO.setUpdateBy(String.valueOf(userId));
            dozerBeanMapper.map(officeDetailVO, officeEntity);
            if (StringUtils.isBlank(officeEntity.getEmail())) {
                officeEntity.setEmail("");
            }
            if (StringUtils.isBlank(officeEntity.getAddress())) {
                officeEntity.setAddress("");
            }
            if (StringUtils.isBlank(officeEntity.getZipCode())) {
                officeEntity.setZipCode("");
            }
            if (StringUtils.isBlank(officeEntity.getPhone())) {
                officeEntity.setPhone("");
            }
            if (StringUtils.isBlank(officeEntity.getFax())) {
                officeEntity.setFax("");
            }
            if (StringUtils.isBlank(officeEntity.getRemarks())) {
                officeEntity.setRemarks("");
            }
            officeMapper.updateByPrimaryKeySelective(officeEntity);
        }
        // ??????????????????parentIds???????????????????????????parentIds
        String oldParentIds = OfficeVo1.getParentIds();

        // ??????????????? parentIds
        OfficeEntity officeEntity1 = new OfficeEntity();
        officeEntity1.setParentIds("%" + officeDetailVO.getId() + "%");
        List<OfficeEntity> byParentIdsLike = officeMapper.findByParentIdsLike(officeEntity1);

        byParentIdsLike.forEach(officeEntity2 -> {
            if (officeEntity2 != null) {
                if ("0".equals(officeDetailVO.getParentId())) {
                    officeEntity2.setParentIds(officeDetailVO.getId());
                } else {
                    if ("0".equals(oldParentIds)) {
                        officeEntity2.setParentIds(officeDetailVO.getParentIds() + "," + officeEntity2.getParentIds());
                    } else {
                        officeEntity2.setParentIds(officeEntity2.getParentIds().replace(oldParentIds, officeDetailVO.getParentIds()));
                    }

                }
                officeMapper.updateParentIds(officeEntity2);
            }
        });

        return officeDetailVO;

    }

    @Override
    public List<OfficeVo> getCurrentUserOfficeTree() {

        UserVo user = UserUtils.getUser();

        String officeId = user.getOffice().getId();


        if (user.isAdmin()) {
            List<OfficeVo> officeVos = officeMapper.listRootOffice();
            return officeVos;
        } else {

            List<OfficeEntity> byChildOffice = officeMapper.findByChildOffice(officeId);

            return byChildOffice.stream().map(children -> {
                return ObjConvert.newInstance(children, OfficeVo.class);

            }).collect(Collectors.toList());

        }
    }

    @Override
    public List<OfficeVo> searchOffice(SearchOfficeDTO searchOfficeDTO) {

        if (StringUtils.isBlank(searchOfficeDTO.getSearchCondition())) {
            List<OfficeVo> officeVos = officeMapper.listRootOffice();
            return officeVos;
        }
        List<OfficeVo> officeVos = officeMapper.searchOffice(searchOfficeDTO);
        return officeVos;
    }

    /**
     * ?????????????????????
     *
     * @param pageNum
     * @param pageSize
     * @param search
     * @param officeIds
     * @return
     */
    @Override
    public Page<AddressBookVo> addressBook(int pageNum, int pageSize, String search, List<String> officeIds) {
        Page<AddressBookVo> page = new Page(pageNum, pageSize);
        //????????????search ?????????ID???????????????????????????????????????
        UserVo user = UserUtils.getUser();
        String parentIds = user.getOffice().getParentIds();

        if (StringUtils.isNotBlank(search)) {
            if (officeIds == null || officeIds.size() == 0) {
                //??????search?????????&&officeIds????????????search???????????????????????????
                //????????????????????????ID
                officeIds = new ArrayList<>();
                List<String> officeRootIds = officeMapper.getOfficeIdByParentId();
                if (officeRootIds != null && officeRootIds.size() > 0) {
                    for (String officeRootId : officeRootIds) {
                        List<String> childIds = officeMapper.getOfficeChildIds(officeRootId);
                        if (childIds != null && childIds.size() > 0) {
                            officeIds.addAll(childIds);
                        }
                    }

                }
            } else {
                //???????????????????????????????????????????????????
                getChildId(officeIds);
            }

        } else {
            //??????search && officeIds ????????????????????????????????????????????????????????????
            if (officeIds == null || officeIds.size() == 0) {
                officeIds = new ArrayList<>();
                if (StringUtils.isNotBlank(parentIds)) {
                    //?????????????????????ID
                    String officeId;
                    String[] parentIdList = parentIds.split(",");
                    if ("0".equals(parentIds)) {
                        officeId = user.getOfficeId();
                    } else {
                        officeId = parentIdList[0];
                    }
                    //??????????????????ID
                    List<String> childIds = officeMapper.getOfficeChildIds(officeId);
                    if (childIds != null && childIds.size() > 0) {
                        officeIds.addAll(childIds);
                    }
                } else {
                    throw new BusinessException(ErrorEnum.PARAMS_WRONG);
                }
            } else {
                //?????????????????????????????????????????????????????????
                getChildId(officeIds);
            }
        }
        //????????????
        if (officeIds == null || officeIds.size() == 0) {
            return null;
        }
        //????????????????????????????????????
        //?????????????????????????????????ID???????????????????????????
        Page<UserEntity> page1 = new Page(pageNum, pageSize);
        List<UserEntity> userEntities = userMapper.getUserByOfficeIds(page1, officeIds, search);
        List<Long> values = new ArrayList<Long>();
        //?????????????????????ID
        if (userEntities != null && userEntities.size() > 0) {
            for (UserEntity userEntitie : userEntities) {
                values.add(userEntitie.getUserId());
            }
        }
        if (values != null && values.size() > 0) {
            //??????UserId????????????????????????
            List<AddressBookVo> addressBookVos = officeMapper.getAddressBookByUserId(values);
            page.setRecords(addressBookVos);
            page.setTotal(page1.getTotal());
        }
        return page;
    }

    public void getChildId(List<String> officeIds) {
        List<OfficeEntity> officeEntities = officeMapper.getOfficeByIds(officeIds);
        for (OfficeEntity officeEntitie : officeEntities) {
            //????????????????????????ID???prentId???0??????????????????????????????ID
            String officeParentIds = officeEntitie.getParentIds();
            if (StringUtils.isNotBlank(officeParentIds)) {
                if ("0".equals(officeEntitie.getParentId())) {
                    String officeId = officeEntitie.getId();
                    //??????????????????ID
                    List<String> childIds = officeMapper.getOfficeChildIds(officeId);
                    if (childIds != null && childIds.size() > 0) {
                        officeIds.addAll(childIds);
                    }
                }
            }
        }
    }

    @Override
    public List<OfficeVo> getRootOffice() {
        //??????????????????????????????
        List<OfficeVo> officeRoots = officeMapper.getRootOfficeMsg();
        return officeRoots;
    }

    @Override
    public OfficeVo getRootOfficeById(String officeId) {
        OfficeVo officeVo = officeMapper.getOfficeById(officeId);
        if (officeVo != null) {
            String parentIds = officeVo.getParentIds();
            if (StringUtils.isNotBlank(parentIds)) {
                String[] parentIdList = parentIds.split(",");
                if ("0".equals(parentIds)) {
                    return officeVo;
                } else {
                    String rootOfficeId = parentIdList[0];
                    OfficeVo officeVo1 = officeMapper.getOfficeById(rootOfficeId);
                    return officeVo1;
                }
            } else {
                throw new BusinessException(ErrorEnum.PARAMS_WRONG);
            }
        } else {
            throw new BusinessException(ErrorEnum.NOT_SELECT_OFFICE_EXIST);
        }

    }

    @Override
    public String getRootOfficeIdByUserId(Long userId) {
        UserEntity userEntity = userMapper.getUserById(userId);
        if (userEntity == null) {
            throw new BusinessException(ErrorEnum.USER_NOT_EXIST);
        }
        String officeId = userEntity.getOfficeId();
        if (StringUtils.isBlank(officeId)) {
            throw new BusinessException(ErrorEnum.USER_NOT_OFFICE);
        }
        OfficeVo officeVo = getRootOfficeById(officeId);
        if (officeVo != null) {
            return officeVo.getId();
        }
        return null;
    }

    @Override
    public List<OfficeVo> getOfficeByName(String name, String parentId) {
        return officeMapper.getOfficeIsExistByName(name, parentId, null, null);
    }

    @Override
    public List<OfficeVo> getOfficeByCode(String code, String parentId) {
        return officeMapper.getOfficeIsExistByName(null, parentId, null, code);
    }

}
