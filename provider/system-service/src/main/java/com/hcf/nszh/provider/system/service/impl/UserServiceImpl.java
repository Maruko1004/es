package com.hcf.nszh.provider.system.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hcf.nszh.common.constant.CacheConstant;
import com.hcf.nszh.common.constant.SystemConstant;
import com.hcf.nszh.common.enums.ErrorEnum;
import com.hcf.nszh.common.exception.BusinessException;
import com.hcf.nszh.common.security.shiro.utils.UserUtils;
import com.hcf.nszh.common.utils.AesUtils;
import com.hcf.nszh.common.utils.StringUtils;
import com.hcf.nszh.provider.system.api.dto.QueryUserPageDto;
import com.hcf.nszh.provider.system.api.dto.SaveUserDto;
import com.hcf.nszh.provider.system.api.dto.UpdatePasswordDTO;
import com.hcf.nszh.provider.system.api.vo.*;
import com.hcf.nszh.provider.system.entity.*;
import com.hcf.nszh.provider.system.mapper.*;
import com.hcf.nszh.provider.system.service.UserService;
import com.hcf.nszh.provider.system.utils.ObjConvert;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.dozer.DozerBeanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author huangxiong
 * @Date 2019/7/1
 **/
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private OfficeMapper officeMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DozerBeanMapper dozerBeanMapper;

    @Autowired
    private RoleUserMapper roleUserMapper;

    @Autowired

    private AreaMapper areaMapper;

    @Override
    public UserVo getUserVoByLoginName(String loginName) {
        //??????????????????????????????????????????????????????????????????????????????????????????
        if (redisTemplate.hasKey(CacheConstant.USER_CACHE +
                CacheConstant.USER_CACHE_LOGIN_NAME_
                + loginName)) {
            UserVo userVo =  JSON.parseObject(redisTemplate.opsForValue().get(CacheConstant.USER_CACHE +
                    CacheConstant.USER_CACHE_LOGIN_NAME_
                    + loginName), UserVo.class);
            //????????????
            UserUtils.clearCache(userVo);
        }
        UserEntity userEntity = userMapper.getByLoginName(loginName);
        if (null == userEntity) {
            return null;
        }
        UserVo userVo = buildUserVo(userEntity);

        userVo.setMenuVoList(buildMenuList(userEntity));

        userVo.setPermissionList(buildPermissionList(userEntity));
        //??????????????????
        redisCache(userVo);
        return userVo;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT,
            timeout = 36000, rollbackFor = Exception.class)
    @Override
    public String updateUserLoginInfo(String loginName, String ip) {
        UserEntity userEntity = userMapper.getByLoginName(loginName);
        // ????????????????????????
        userEntity.setLoginIp(ip);
        userEntity.setLoginDate(new Date());
        userMapper.updateById(userEntity);
        return ErrorEnum.SUCCESS.getMessage();
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000,
            rollbackFor = Exception.class)
    @Override
    public String saveOrUpdateUser(SaveUserDto saveUserDto) {

        UserVo user = UserUtils.getUser();
        UserEntity userEntity ;
        //??????????????????????????????
        if(saveUserDto.getIsNewRecord()){
             userEntity = userMapper.getUserByLoginName(saveUserDto.getLoginName(),null);
        }else {
            Long userId = saveUserDto.getUserId();
            if(userId == null || userId == 0){
                throw new BusinessException(ErrorEnum.PARAMS_WRONG);
            }
            userEntity = userMapper.getUserByLoginName(saveUserDto.getLoginName(),userId);
        }

        if (null != userEntity ) {
            throw new BusinessException(ErrorEnum.USER_USRE_REPEAT);
        }

        if (saveUserDto.getIsNewRecord()) {
            userEntity = new UserEntity();
            dozerBeanMapper.map(saveUserDto, userEntity);
            if (StringUtils.isNotBlank(saveUserDto.getPassword())) {
                userEntity.setPassword(AesUtils.entryptPassword(AesUtils.aesDecrypt(saveUserDto.getPassword())));
            }else{
                throw new BusinessException(ErrorEnum.USER_PASSWORD_EMPTY);
            }
            Date date = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
            userEntity.setCreateTime(date);
            userEntity.setUpdateTime(date);
            userEntity.setCreateUserId(String.valueOf(user.getUserId()));
            userEntity.setUpdateUserId(String.valueOf(user.getUserId()));
            userEntity.setDelFlag(SystemConstant.DEL_FLAG_NORMAL);
            userMapper.insert(userEntity);
        } else if (!saveUserDto.getIsNewRecord() ) {
            userEntity = new UserEntity();
            UserEntity userEntity1 = userMapper.getUserById(saveUserDto.getUserId());
            if(userEntity1 == null){
                throw new BusinessException(ErrorEnum.USER_NOT_EXIST);
            }
            dozerBeanMapper.map(saveUserDto, userEntity);
            if (StringUtils.isNotBlank(saveUserDto.getPassword())) {
                userEntity.setPassword(AesUtils.entryptPassword(AesUtils.aesDecrypt(saveUserDto.getPassword())));
            }
            if(StringUtils.isBlank(userEntity.getPhone())){
                userEntity.setPhone("");
            }
            if(StringUtils.isBlank(userEntity.getEmail())){
                userEntity.setEmail("");
            }
            Date date = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
            userEntity.setUpdateTime(date);
            userEntity.setUpdateUserId(String.valueOf(user.getUserId()));
            userEntity.setOfficeId(saveUserDto.getOfficeId());
            userMapper.updateByPrimaryKeySelective(userEntity);
        }
        UserVo userVo = new UserVo();
        dozerBeanMapper.map(userEntity, userVo);
        userMapper.deleteUserRole(String.valueOf(userEntity.getUserId()));
        //??????????????????
        saveUserDto.setUserId(userEntity.getUserId());
        if (saveUserDto.getRoleIds() != null && saveUserDto.getRoleIds().size() > 0) {
            userMapper.insertUserRole(saveUserDto);
        } else {
            throw new BusinessException(ErrorEnum.USER_ROLE_EMPTY);
        }
        UserUtils.clearCache(userVo);
        if(!saveUserDto.getIsNewRecord()){
            if(saveUserDto.getUserId() != null && saveUserDto.getUserId().equals(user.getUserId()) ){
                //???????????????????????????????????????????????????????????????
                UserEntity userEntity1 = userMapper.getByLoginName(saveUserDto.getLoginName());
                if(userEntity1 != null){
                    UserVo userVo1 = buildUserVo(userEntity1);
                    userVo.setMenuVoList(buildMenuList(userEntity1));
                    userVo.setPermissionList(buildPermissionList(userEntity1));
                    redisCache(userVo1);
                }
            }
        }
        return null;
    }

    //????????????
    public void redisCache(UserVo userVo){
        redisTemplate.opsForValue().set(CacheConstant.USER_CACHE + CacheConstant.USER_CACHE_USER_ID_
                        + userVo.getUserId(), JSON.toJSONString(userVo), CacheConstant.USER_CACHE_USER_TIMEOUT,
                TimeUnit.MINUTES);

        redisTemplate.opsForValue().set(CacheConstant.USER_CACHE + CacheConstant.USER_CACHE_ID_
                        + userVo.getUserId(), JSON.toJSONString(userVo), CacheConstant.USER_CACHE_USER_TIMEOUT,
                TimeUnit.MINUTES);

        redisTemplate.opsForValue().set(CacheConstant.USER_CACHE + CacheConstant.USER_CACHE_LOGIN_NAME_
                        + userVo.getLoginName(), JSON.toJSONString(userVo),
                CacheConstant.USER_CACHE_USER_TIMEOUT, TimeUnit.MINUTES);
    }

    @Override
    public List<UserVo> findRoledUser(String roleId, String officeId) {
        List<UserEntity> userEntityList = userMapper.selectRoleUser(roleId, officeId);
        List<UserVo> userVoList = new ArrayList<UserVo>();
        if (null != userEntityList) {
            userEntityList.stream().forEach(a -> {
                UserVo userVo = new UserVo();
                dozerBeanMapper.map(a, userVo);
                userVoList.add(userVo);
            });
        }
        return userVoList;
    }

    @Override
    public Page<UserPageVo> list(QueryUserPageDto queryUserPageDto) {
        Page<UserPageVo> page = new Page(queryUserPageDto.getPageNum(), queryUserPageDto.getPageSize());
        List<UserPageVo> list = userMapper.queryUserPage(page, queryUserPageDto);
        if (CollectionUtils.isEmpty(list)) {
            page.setRecords(list);
            return page;
        }

        try {
            list.forEach(userPageVo -> {
                List<RoleUserVO> roleUserVOS = roleUserMapper.selectByUserId(userPageVo.getUserId());
                List<String> roleIds = roleUserVOS.stream().map(roleUserVO -> roleUserVO.getRoleId()).collect(Collectors.toList());
                List<String> roleNames = roleIds.stream().map(roleId -> {
                    RoleDetailVO byRoleId = roleMapper.getByRoleId(roleId);
                    if (null == byRoleId) {
                        return "";
                    }
                    return byRoleId.getName();
                }).collect(Collectors.toList());
                OfficeVo officeById = officeMapper.getOfficeById(userPageVo.getOfficeId());
                if (officeById == null) {
                    userPageVo.setOfficeName(null);
                }else{
                    userPageVo.setOfficeName(officeById.getName());
                }

                userPageVo.setRoleNames(roleNames);
                userPageVo.setRoleIds(roleIds);
            });
            page.setRecords(list);
        } catch (NullPointerException e) {
            log.error("????????????,{}", e);
        }
        return page;
    }

    @Override
    public UserVo getUserId(Long userId) {
        if (redisTemplate.hasKey(CacheConstant.USER_CACHE +
                CacheConstant.USER_CACHE_USER_ID_
                + userId)) {
            return  JSON.parseObject(redisTemplate.opsForValue().get(CacheConstant.USER_CACHE +
                    CacheConstant.USER_CACHE_USER_ID_
                    + userId), UserVo.class);
        }
        UserEntity userEntity = userMapper.getUserById(userId);
        if (null == userEntity) {
            return null;
        }
        UserVo userVo = buildUserVo(userEntity);
        redisTemplate.opsForValue().set(CacheConstant.USER_CACHE + CacheConstant.USER_CACHE_USER_ID_
                + userEntity.getUserId(), JSON.toJSONString(userVo), CacheConstant.USER_CACHE_USER_TIMEOUT,
                TimeUnit.MINUTES);
        return userVo;
    }

    @Override
    public UserVo getUserByUserId(Long userId) {
        UserEntity userEntity = userMapper.getUserById(userId);
        if (null == userEntity) {
            return null;
        }
        UserVo userVo = buildUserVo(userEntity);
        return userVo;
    }

    @Override
    public UserVo infoData() {
        UserVo user = UserUtils.getUser();
        String loginName = user.getLoginName();
        if(StringUtils.isNotBlank(loginName)){
                UserEntity userEntity = userMapper.getByLoginName(loginName);
                if (null == userEntity) {
                    throw new BusinessException(ErrorEnum.USER_ACCOUNT_UNKNOWN);
                }
                UserVo userVo = buildUserVo(userEntity);
                userVo.setMenuVoList(buildMenuList(userEntity));
                userVo.setPermissionList(buildPermissionList(userEntity));
                return userVo;

        }else{
            throw new BusinessException(ErrorEnum.USER_EXPIRED_ERROR);
        }


    }

    private OfficeVo getRootOffice(OfficeVo officeVo){
       if(officeVo != null){
            String parentIds = officeVo.getParentIds();
            if(StringUtils.isNotBlank(parentIds)){
                //?????????????????????ID
                //???????????????ID
                String rootOfficeId;
                String[] parentIdList  = parentIds.split(",");
                if("0".equals(parentIds)){
                    rootOfficeId = officeVo.getId();
                }else{
                    rootOfficeId = parentIdList[0];
                }
                //????????????????????????
                OfficeVo officeVo1 = officeMapper.getOfficeById(rootOfficeId);
                 return officeVo1;
            }
        }
        return null;
    }

    private UserVo buildUserVo(UserEntity userEntity) {
        UserVo userVo = new UserVo();
        dozerBeanMapper.map(userEntity, userVo);
        if (StringUtils.isNotBlank(userEntity.getOfficeId())) {
            OfficeEntity officeEntity = officeMapper.findOfficeById(userEntity.getOfficeId());
            if (null != officeEntity) {
                OfficeVo officeVo = new OfficeVo();
                dozerBeanMapper.map(officeEntity, officeVo);
                userVo.setOffice(officeVo);
                //???????????????????????????id?????????
                OfficeVo rootOfficeVo =  getRootOffice(officeVo);
                if(rootOfficeVo != null){
                    userVo.setRootOfficeId(rootOfficeVo.getId());
                    userVo.setRootOfficeName(rootOfficeVo.getName());
                }
            }
            AreaEntity areaEntity = areaMapper.findAreaById(userEntity.getOffice().getArea().getId());
            if(null != areaEntity){
                AreaVo areaVo = new AreaVo();
                dozerBeanMapper.map(areaEntity,areaVo);
                userVo.setArea(areaVo);
            }
        }

        //userVo.setMenuVoList(buildMenuList(userEntity));
        //?????????
        List<RoleEntity> roleEntityList = roleMapper.findList(userEntity.getUserId(), null,
                SystemConstant.DEL_FLAG_NORMAL, SystemConstant.YES);
        List<RoleVo> roleVoList = new ArrayList<>();
        if (null != roleEntityList) {
            roleEntityList.stream().forEach(a -> {
                RoleVo roleVo = new RoleVo();
                dozerBeanMapper.map(a, roleVo);
                roleVo.setRoleName(a.getName());
                roleVoList.add(roleVo);
            });
        }
        userVo.setRoleList(roleVoList);

        return userVo;
    }

    private List<MenuVo> buildMenuList(UserEntity userEntity) {
        //?????????
        MenuEntity m = new MenuEntity();
        m.setUserId(userEntity.getUserId());
        m.setDelFlag(SystemConstant.DEL_FLAG_NORMAL);
        List<MenuEntity> menuList   = menuMapper.findByUserId(m);

        List<MenuVo> menuVoList = new ArrayList<>();
        if (null != menuList) {
            menuList.stream().forEach(a -> {
                MenuVo menuVo = new MenuVo();
                dozerBeanMapper.map(a, menuVo);
                if (StringUtils.isBlank(menuVo.getParentId())) {
                    menuVo.setParentId("0");
                }
                menuVoList.add(menuVo);
            });
        }
        return menuVoList;
    }

    /**
     * ??????????????????
     *
     * @param userEntity
     * @return
     */
    private List<String> buildPermissionList(UserEntity userEntity) {
        //?????????
        MenuEntity m = new MenuEntity();
            m.setUserId(userEntity.getUserId());
            m.setDelFlag(SystemConstant.DEL_FLAG_NORMAL);
        List<MenuEntity>   menuList = menuMapper.findByUserId(m);
        StringUtils.removeDuplicateWithList(menuList);
        List<String> menuVoList = new ArrayList<>();
        if (null != menuList) {
            menuList.stream().filter(a -> StringUtils.isNotBlank(a.getPermission())).forEach(a -> {
                menuVoList.add(a.getPermission());
            });
        }
        return menuVoList;
    }


    @Override
    public List<UserPageVo> listUserExceptTeam(List<Long> userIds) {
        List<UserPageVo> userPageVos = userMapper.listUser(userIds);
        return userPageVos;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000,
            rollbackFor = Exception.class)
    @Override
    public UserVo modifyPassword(UpdatePasswordDTO updatePasswordDTO) {
        String newPassword = null;
        String oldPassword = null;
        if (updatePasswordDTO.getNewPassword() != null) {
            newPassword = AesUtils.aesDecrypt(updatePasswordDTO.getNewPassword());
        }
        if (updatePasswordDTO.getOldPassword() != null) {
            oldPassword = AesUtils.aesDecrypt(updatePasswordDTO.getOldPassword());
        }
        UserVo user = UserUtils.getUser();
        if (AesUtils.validatePassword(oldPassword, user.getPassword())) {
            UserEntity userEntity = new UserEntity();
            userEntity.setUserId(user.getUserId());
            userEntity.setNewPassword(AesUtils.entryptPassword(newPassword));
            userMapper.updatePasswordById(userEntity);
            // ??????????????????
            user.setLoginName(userEntity.getLoginName());
            UserUtils.clearCache(user);
        }
        UserVo userVo = new UserVo();
        userVo.setLoginName(updatePasswordDTO.getLoginName());
        return userVo;
    }

    @Override
    public List<UserPageVo> searchUserByName(String userName) {

        if (StringUtils.isBlank(userName)) {
            List<UserEntity> userEntities = userMapper.listAllUser();
            userEntities.stream().map(userEntity -> {
                UserPageVo userPageVo = ObjConvert.newInstance(userEntity, UserPageVo.class);
                return userPageVo;
            }).collect(Collectors.toList());
        }
        List<UserEntity> userByNameLike = userMapper.getUserByNameLike(userName);
        List<UserPageVo> userPageVos = userByNameLike.stream().map(userEntity -> {
            UserPageVo userPageVo = ObjConvert.newInstance(userEntity, UserPageVo.class);
            return userPageVo;
        }).collect(Collectors.toList());
        return userPageVos;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 36000,
            rollbackFor = Exception.class)
    @Override
    public String deleteUser(Long userId) {
        UserEntity userEntity = userMapper.getUserById(userId);
        userEntity.setDelFlag(SystemConstant.DEL_FLAG_DELETE);
        userMapper.updateUserDelFlagByUserId(userEntity.getUserId());

        //???????????????????????????
        roleUserMapper.deleteByUserId(userId);
        UserVo userVo = new UserVo();
        dozerBeanMapper.map(userEntity, userVo);
        UserUtils.clearCache(userVo);
        return "OK";
    }


    @Override
    public List<UserVo> listAllUser() {

        List<UserEntity> userEntities = userMapper.listAllUser();
        if (CollectionUtils.isEmpty(userEntities)) {
            return new ArrayList<>(0);
        }

        List<UserVo> userVoList = userEntities.stream().map(userEntity -> {
            UserVo userVo = ObjConvert.newInstance(userEntity, UserVo.class);
            return userVo;
        }).collect(Collectors.toList());
        return userVoList;
    }

    @Override
    public Map<String,Object> findUserByRoleId(String roleId,int pageNumber,int pageSize) {
        Page<UserByRoleVo> page = new Page<>(pageNumber, pageSize);
        List<UserByRoleVo> userEntityList = userMapper.selectUserByRole(page,roleId,null);
        RoleVo roleVo = new RoleVo();
        page.setRecords(userEntityList);

        RoleDetailVO roleDetailVO = roleMapper.getByRoleId(roleId);
        dozerBeanMapper.map(roleDetailVO, roleVo);

        Map<String,Object> map = new HashMap<>();
        map.put("role",roleVo);
        map.put("page",page);
        return map;
    }

    @Override
    public List<UserBaseVo> unRoledUser(String roleId) {
        if(StringUtils.isBlank(roleId)){
            throw new BusinessException(ErrorEnum.PARAMS_WRONG);
        }
        return userMapper.selectUserOfNoRole(roleId);
    }

    @Override
    public List<OfficeOfUserVo> unRoledOfficeOfUser(String roleId) {
        if(StringUtils.isBlank(roleId)){
            throw new BusinessException(ErrorEnum.PARAMS_WRONG);
        }
        try {
            List<OfficeOfUserVo> officeOfUserVos = officeMapper.officeOfUser(roleId);
            return officeOfUserVos;
        }catch (Exception e){
            log.warn("????????????{}",e);
            return null;
        }
    }

    //????????????????????????
    @Override
    public List<UserBaseVo> getMyOfficeUser(String roleCode) {
        //???????????????????????????
        UserVo user = UserUtils.getUser();
        String officeId = user.getOfficeId();
        List<AddressBookVo> addressBookVos;
        List<String> officeIds = new ArrayList<>();
            //?????????????????? ??????/?????? ????????????
            //??????????????????????????????ID
            //?????????????????????ID
            OfficeVo officeVo = officeMapper.getOfficeById(officeId);
            if(officeVo != null){
                String parentIds = officeVo.getParentIds();
                if(StringUtils.isNotBlank(parentIds)){
                    //?????????????????????ID
                    String rootOfficeId;
                    String[] parentIdList  = parentIds.split(",");
                    if("0".equals(parentIds)){
                        rootOfficeId = officeVo.getId();
                    }else{
                        rootOfficeId = parentIdList[0];
                    }
                    officeIds.add(rootOfficeId);
                    //??????????????????ID
                    List<String> childIds = officeMapper.getOfficeChildIds(rootOfficeId);
                    if(childIds != null && childIds.size() > 0){
                        officeIds.addAll(childIds);
                    }
                    //roleCode????????????????????????????????????
                   addressBookVos = officeMapper.getAddressBook(null,officeIds,null,roleCode);

                }else{
                    throw new BusinessException(ErrorEnum.PARAMS_WRONG);
                }
            }else{
                throw new BusinessException(ErrorEnum.PARAMS_WRONG);
            }
        if(addressBookVos == null || addressBookVos.size() == 0){
            return null;
        }
        List<UserBaseVo> userBaseVos = addressBookVos.stream().map(addressBookVo -> {
            return ObjConvert.newInstance(addressBookVo, UserBaseVo.class);
        }).collect(Collectors.toList());
       return userBaseVos;

    }

    @Override
    public List<OfficeOfUserVo> getAllOfficeAuditUser(String roleCode) {
        //??????????????????????????????
        List<OfficeVo> officeRoots =  officeMapper.getRootOfficeMsg();


        List<OfficeOfUserVo> officeOfUserVos = officeRoots.stream().map(officeRoot -> {
            OfficeOfUserVo officeOfUserVo = new OfficeOfUserVo();
            //??????????????????ID
            List<String> officeIds = new ArrayList<>();
            List<String> childIds = officeMapper.getOfficeChildIds(officeRoot.getId());
            if(childIds != null && childIds.size() > 0){
                officeIds.addAll(childIds);
            }
            officeIds.add(officeRoot.getId());
            //????????????
            List<AddressBookVo>   addressBookVos = officeMapper.getAddressBook(null,officeIds,null,roleCode);
            List<UserBaseVo> userBaseVos = addressBookVos.stream().map(addressBookVo -> {
                return ObjConvert.newInstance(addressBookVo, UserBaseVo.class);
            }).collect(Collectors.toList());
            officeOfUserVo.setUserBaseVos(userBaseVos);
            officeOfUserVo.setId(officeRoot.getId());
            officeOfUserVo.setName(officeRoot.getName());
            return officeOfUserVo;
        }).collect(Collectors.toList());

        return officeOfUserVos;
    }

    @Override
    public String getMyOfficeRootId() {
        UserVo user = UserUtils.getUser();
        if(user == null){
            throw new BusinessException(ErrorEnum.USER_EXPIRED_ERROR);
        }
        String parentIds = user.getOffice().getParentIds();
        if(StringUtils.isNotBlank(parentIds)) {
            //?????????????????????ID
            String officeId;//???????????????ID
            String[] parentIdList = parentIds.split(",");
            if ("0".equals(parentIds)) {
                officeId = user.getOfficeId();
            } else {
                officeId = parentIdList[0];
            }
            return officeId;
        }
        return null;
    }

    @Override
    public UserEntity getUserByLoginName(String loginName) {
        UserEntity userEntity = userMapper.getUserByLoginName(loginName,null);
        return userEntity;
    }

    @Override
    public Boolean saveSkin(String userId, String skin ,String sysSkin) {
        return userMapper.saveSkin(userId,skin,sysSkin);
    }


}
