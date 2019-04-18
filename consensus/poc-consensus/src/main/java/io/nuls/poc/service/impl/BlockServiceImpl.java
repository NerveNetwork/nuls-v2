package io.nuls.poc.service.impl;

import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.data.Block;
import io.nuls.base.data.BlockHeader;
import io.nuls.poc.constant.ConsensusConstant;
import io.nuls.poc.constant.ConsensusErrorCode;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.model.dto.input.ValidBlockDTO;
import io.nuls.poc.service.BlockService;
import io.nuls.poc.utils.manager.BlockManager;
import io.nuls.poc.utils.manager.ChainManager;
import io.nuls.poc.utils.validator.BlockValidator;
import io.nuls.rpc.util.RPCUtil;
import io.nuls.tools.basic.Result;
import io.nuls.tools.core.annotation.Autowired;
import io.nuls.tools.core.annotation.Service;
import io.nuls.tools.exception.NulsException;
import io.nuls.tools.parse.JSONUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共识模块RPC接口实现类
 * Consensus Module RPC Interface Implementation Class
 *
 * @author tag
 * 2018/11/7
 */
@Service
public class BlockServiceImpl implements BlockService {

    @Autowired
    private ChainManager chainManager;

    @Autowired
    private BlockManager blockManager;

    @Autowired
    private BlockValidator blockValidator;
    /**
     * 缓存最新区块
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result addBlock(Map<String, Object> params) {
        if (params.get(ConsensusConstant.PARAM_CHAIN_ID) == null || params.get(ConsensusConstant.PARAM_BLOCK_HEADER) == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        int chainId = (Integer) params.get(ConsensusConstant.PARAM_CHAIN_ID);
        if (chainId <= ConsensusConstant.MIN_VALUE) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        try {
            String headerHex = (String) params.get(ConsensusConstant.PARAM_BLOCK_HEADER);
            BlockHeader header = new BlockHeader();
            header.parse(RPCUtil.decode(headerHex), 0);
            blockManager.addNewBlock(chain, header);
            Map<String, Object> validResult = new HashMap<>(2);
            validResult.put("value", true);
            return Result.getSuccess(ConsensusErrorCode.SUCCESS).setData(validResult);
        } catch (NulsException e) {
            chain.getLoggerMap().get(ConsensusConstant.BASIC_LOGGER_NAME).error(e);
            return Result.getFailed(e.getErrorCode());
        }
    }

    /**
     * 链分叉区块回滚
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result chainRollBack(Map<String, Object> params) {
        if (params.get(ConsensusConstant.PARAM_CHAIN_ID) == null || params.get(ConsensusConstant.PARAM_HEIGHT) == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        int chainId = (Integer) params.get(ConsensusConstant.PARAM_CHAIN_ID);
        if (chainId <= ConsensusConstant.MIN_VALUE) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        int height = (Integer) params.get(ConsensusConstant.PARAM_HEIGHT);
        blockManager.chainRollBack(chain, height);
        Map<String, Object> validResult = new HashMap<>(2);
        validResult.put("value", true);
        return Result.getSuccess(ConsensusErrorCode.SUCCESS).setData(validResult);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result receiveHeaderList(Map<String, Object> params) {
        if (params.get(ConsensusConstant.PARAM_CHAIN_ID) == null || params.get(ConsensusConstant.HEADER_LIST) == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        int chainId = (Integer) params.get(ConsensusConstant.PARAM_CHAIN_ID);
        if (chainId <= ConsensusConstant.MIN_VALUE) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        try {
            List<String> headerList = (List<String>) params.get(ConsensusConstant.HEADER_LIST);
            List<BlockHeader> blockHeaderList = new ArrayList<>();
            for (String header:headerList) {
                BlockHeader blockHeader = new BlockHeader();
                blockHeader.parse(RPCUtil.decode(header),0);
                blockHeaderList.add(blockHeader);
            }
            List<BlockHeader> localBlockHeaders = chain.getBlockHeaderList();
            System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
            System.out.println("headerList.size" + headerList.size());
            System.out.println("rev headerList.begin" + blockHeaderList.get(0).getHeight());
            System.out.println("rev headerList.end" + blockHeaderList.get(blockHeaderList.size() - 1).getHeight());
            System.out.println("local.begin" + localBlockHeaders.get(0).getHeight());
            System.out.println("local.end" + localBlockHeaders.get(localBlockHeaders.size() - 1).getHeight());
            localBlockHeaders.addAll(0, blockHeaderList);
            Map<String, Object> validResult = new HashMap<>(2);
            validResult.put("value", true);
            return Result.getSuccess(ConsensusErrorCode.SUCCESS).setData(validResult);
        } catch (NulsException e) {
            chain.getLoggerMap().get(ConsensusConstant.BASIC_LOGGER_NAME).error(e);
            return Result.getFailed(e.getErrorCode());
        }
    }

    /**
     * 验证区块正确性
     */
    @Override
    public Result validBlock(Map<String, Object> params) {
        if (params == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        ValidBlockDTO dto = JSONUtils.map2pojo(params, ValidBlockDTO.class);
        if (dto.getChainId() <= ConsensusConstant.MIN_VALUE || dto.getBlock() == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        int chainId = dto.getChainId();
        /*
         * 0区块下载中，1接收到最新区块
         * */
        boolean isDownload = (dto.getDownload() == 0);
        String blockHex = dto.getBlock();
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        try {
            Block block = new Block();
            block.parse(new NulsByteBuffer(RPCUtil.decode(blockHex)));
            blockValidator.validate(isDownload, chain, block);
            return Result.getSuccess(ConsensusErrorCode.SUCCESS);
        } catch (NulsException e) {
            chain.getLoggerMap().get(ConsensusConstant.BASIC_LOGGER_NAME).error(e);
            return Result.getFailed(e.getErrorCode());
        } catch (IOException e) {
            chain.getLoggerMap().get(ConsensusConstant.BASIC_LOGGER_NAME).error(e);
            return Result.getFailed(ConsensusErrorCode.DATA_PARSE_ERROR);
        }
    }
}
