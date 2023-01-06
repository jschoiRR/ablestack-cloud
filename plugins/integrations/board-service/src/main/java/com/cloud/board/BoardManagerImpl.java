// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.cloud.board;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
// import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.command.admin.board.AddBoardCmd;
import org.apache.cloudstack.api.command.admin.board.AddBoardFilesCmd;
import org.apache.cloudstack.api.command.admin.board.DeleteBoardCmd;
import org.apache.cloudstack.api.command.admin.board.ListBoardCmd;
import org.apache.cloudstack.api.command.admin.board.UpdateBoardCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.BoardResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.log4j.Logger;

import com.cloud.api.ApiDBUtils;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.domain.Domain;
import com.cloud.event.ActionEvent;
import com.cloud.board.dao.BoardDao;
import com.cloud.board.dao.BoardDaoImpl;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Filter;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.exception.InvalidParameterValueException;

public class BoardManagerImpl extends ManagerBase implements BoardService {
    public static final Logger LOGGER = Logger.getLogger(BoardDaoImpl.class.getName());

    @Inject
    private BoardDao boardDao;
    @Inject
    protected AccountService accountService;
    @Inject
    private DataCenterDao dataCenterDao;

    public BoardResponse addBoardFiles(final AddBoardFilesCmd cmd) {
        if (!BoardEnabled.value()) {
            throw new CloudRuntimeException("Board plugin is disabled");
        }

        final Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());

        BoardVO boardVO = new BoardVO();
        // boardDao.persist(boardVO);

        return createBoardResponse(boardVO);
    }

    @Override
    @ActionEvent(eventType = BoardEventTypes.EVENT_BOARD_ADD, eventDescription = "Adding Board")
    public BoardResponse addBoard(final AddBoardCmd cmd) {
        if (!BoardEnabled.value()) {
            throw new CloudRuntimeException("Board plugin is disabled");
        }

        final Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());

        final String title = cmd.getTitle();
        final String type = cmd.getType();
        final String content = cmd.getContent();
        final String file = cmd.getFile();
        final Long domainId = owner.getDomainId();
        final Long accountId = owner.getAccountId();

        BoardVO boardVO = new BoardVO(title, accountId, domainId, type, content, file);
        boardDao.persist(boardVO);

        return createBoardResponse(boardVO);
    }

    private BoardResponse createBoardResponse(final Board board) {
        BoardVO rrvo = boardDao.findById(board.getId());
        BoardResponse response = new BoardResponse();
        response.setObjectName("board");
        response.setId(board.getUuid());
        response.setTitle(board.getTitle());
        response.setContents(board.getContent());
        response.setHit(board.getHit());
        response.setType(board.getType());
        response.setCreated(board.getCreated());

        Account account = ApiDBUtils.findAccountById(board.getAccountId());
        response.setAccountName(account.getAccountName());

        Domain domain = ApiDBUtils.findDomainById(rrvo.getDomainId());
        response.setDomainId(domain.getUuid());
        response.setDomainName(domain.getName());

        return response;
    }

    private ListResponse<BoardResponse> createBoardListResponse(List<BoardVO> bv) {
        List<BoardResponse> responseList = new ArrayList<>();
        for (BoardVO r : bv) {
            responseList.add(createBoardResponse(r));
        }
        ListResponse<BoardResponse> response = new ListResponse<>();

        response.setResponses(responseList);
        return response;
    }

    @Override
    public ListResponse<BoardResponse> listBoard(final ListBoardCmd cmd) {
        if (!BoardEnabled.value()) {
            throw new CloudRuntimeException("Board plugin is disabled");
        }
        final Long id = cmd.getId();
        final Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());
        final Long accountId = owner.getAccountId();
        final Long domainId = owner.getDomainId();
        final String type = cmd.getType();

        Filter searchFilter = new Filter(BoardVO.class, "created", false, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<BoardVO> sb = boardDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("type", sb.entity().getType(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getTitle(), SearchCriteria.Op.LIKE);
        SearchCriteria<BoardVO> sc = sb.create();
        String keyword = cmd.getKeyword();
        if (id != null) {
            boardDao.increaseHit(id);
            sc.setParameters("id", id);
        }
        if (type != null) {
            sc.setParameters("type", type);
        }
        if (keyword != null) {
            sc.addOr("uuid", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        List<BoardVO> bv = boardDao.search(sc, searchFilter);

        return createBoardListResponse(bv);
    }

    @Override
    @ActionEvent(eventType = BoardEventTypes.EVENT_BOARD_DELETE, eventDescription = "Deleting Board", async = true)
    public boolean deleteBoard(final DeleteBoardCmd cmd) {
        if (!BoardEnabled.value()) {
            throw new CloudRuntimeException("Board plugin is disabled");
        }
        final Long id = cmd.getId();
        Board rr = boardDao.findById(id);

        if (rr == null) {
            throw new InvalidParameterValueException("Invalid Board id specified");
        }

        return boardDao.remove(rr.getId());
    }

    @Override
    @ActionEvent(eventType = BoardEventTypes.EVENT_BOARD_UPDATE, eventDescription = "Updating Board")
    public BoardResponse updateBoard(final UpdateBoardCmd cmd) {
        if (!BoardEnabled.value()) {
            throw new CloudRuntimeException("Board plugin is disabled");
        }
        final Long id = cmd.getId();
        // Board.State state = null;
        BoardVO bv = boardDao.findById(id);
        if (bv == null) {
            throw new InvalidParameterValueException("Invalid Board id specified");
        }

        final String title = cmd.getTitle();
        final String content = cmd.getContent();
        final String type = cmd.getType();

        bv = boardDao.createForUpdate(bv.getId());
        bv.setTitle(title);
        bv.setContent(content);
        bv.setType(type);

        if (!boardDao.update(bv.getId(), bv)) {
            throw new CloudRuntimeException(String.format("Failed to update Board ID: %s", bv.getUuid()));
        }
        bv = boardDao.findById(bv.getId());
        return createBoardResponse(bv);
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!BoardEnabled.value()) {
            return cmdList;
        }

        cmdList.add(ListBoardCmd.class);
        cmdList.add(AddBoardCmd.class);
        cmdList.add(AddBoardFilesCmd.class);
        cmdList.add(DeleteBoardCmd.class);
        cmdList.add(UpdateBoardCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return BoardService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                BoardEnabled
        };
    }
}