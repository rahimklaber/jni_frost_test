use bitcoin::consensus::deserialize;
use std::borrow::Borrow;
use std::collections::HashMap;
use std::str::FromStr;

use crate::{
    ResultKeygen1, ResultKeygen2, SchnorrKeyGenWrapper, SchnorrSignWrapper, SchnorrSingleSignTest,
    SignParams2, ScriptContainer,
};
use bitcoin::blockdata::constants::COIN_VALUE;
use bitcoin::psbt::serialize::Deserialize;
use bitcoin::psbt::Psbt;
use bitcoin::schnorr::{TapTweak, UntweakedPublicKey};
use bitcoin::util::sighash;
use bitcoin::util::sighash::Error::WrongAnnex;
use bitcoin::util::sighash::SighashCache;
use bitcoin::util::taproot::{TapTweakHash, TaprootBuilder};
use bitcoin::{SchnorrSighashType, Script, Transaction, TxOut};
use bitcoin_serai::crypto::{make_even, Hram as BitcoinHram};
use k256::elliptic_curve::ops::Reduce;
use k256::elliptic_curve::sec1::ToEncodedPoint;
use k256::{Scalar, U256};
use modular_frost::dkg::frost::Commitments;
use modular_frost::{
    algorithm::{Hram, SchnorrSignature},
    curve::Secp256k1,
};
use secp256k1::{
    hashes::{sha256, Hash},
    Message, XOnlyPublicKey,
};
use sha2::{Digest, Sha256};

#[test]
fn test() {
    let mut scripts = ScriptContainer::new();
    scripts.add_script(
        &[1,2,3]
    );
    println!("scripts: {:?}", scripts);
    return;
    // let wrapper = SchnorrSingleSignTest::new_from_bytes(hex::decode("3e6c3783aa5cf8952b0d7f4e7942c6556b262b3d903679747f93ebd9a5730105").unwrap().as_slice());
    //
    // println!("{}",hex::encode(wrapper.keypair.secret_key().secret_bytes()));
    // println!("{}", hex::encode(wrapper.get_bitcoin_encoded_key().into_iter().map(|x|x as u8).collect::<Vec<u8>>()));
    // // println!("{}",hex::encode(wrapper.keypair.public_key().serialize().as_slice()));
    // let msg = hex::decode("01000000016862283ac53bdd39c2e37753946cef31198283c09b429b75fcaa310e06ade2880000000000ffffffff01e803000000000000225120fa9e200cc285ddf29759942b900e75371f9d64c6b5bc918a23bf2c99532818dd00000000").unwrap();
    // let script = hex::decode("512100a18b1990a86d7ad28880a6f51d351ab6e889b3209e1b69e4b4848f7f078357fa").unwrap();
    // let msg_i8 = unsafe { &*(msg.as_slice() as *const _  as *const [i8]) };
    // let script_i8 = unsafe { &*(script.as_slice() as *const _  as *const [i8]) };
    // wrapper.sign_tx(
    //     msg_i8,
    //     script_i8
    // );
    //
    // let key = XOnlyPublicKey::from_str("7a7e0dcadb3c9ef6584768c6ff9cdbdcac2c124f7233685d7cb1a8b5066e7645").unwrap();

    // println!("key: {:?}", hex::encode(key.serialize().as_slice()));

    // let sig_obj = secp256k1::schnorr::Signature::from_slice(
    //     hex::decode("820c4ee7354662753ab4aa89d94aeebd35bb8d5f3d370a49927a8d8eb468cce979864645e0ea9637d51b883e501b0bfa7fc9391dcfb1a2abf405a139f1ca1ce6").unwrap().as_slice()
    // ).unwrap();
    // let msg = Message::from_slice(hex::decode("4f0719c29f2088f5b662a9877c3bd243b9f389dd24933481e518ae70f7e034ed").unwrap().as_slice()).unwrap();

    // sig_obj.verify(&msg,&key).unwrap();

    // let mut tx = deserialize::<Transaction>(hex::decode("0100000000010123d71190efeeba9059df6d78cb2d136c7b515175e3888ce7b24c75ea24e027610000000000ffffffff01e803000000000000225120c2a9f280d11fda828c25912a1f6bfaa0294e130c72c71c658846fdc1aed0aa810140779ed4753bf9563d20a54c58cba969bdfffc615805ce196e36d87d5e9b19529b31c5ed59c6f9502094b07d21578e7ad70b10929f5f5f9652550d1c69ffcb346100000000").unwrap().as_slice()).unwrap();
    // tx.input[0].witness.clear();
    // println!("{:?}",tx);
    // let psbt = Psbt::from_unsigned_tx(tx).unwrap();
    // println!("root : {:?}", psbt.inputs[0].tap_merkle_root);
    //
    // let hash = SighashCache::new(&psbt.unsigned_tx).taproot_key_spend_signature_hash(
    //     0,
    //     &sighash::Prevouts::All(&[TxOut {
    //         value: COIN_VALUE /100,
    //         script_pubkey: Script::deserialize(hex::decode("5120c2a9f280d11fda828c25912a1f6bfaa0294e130c72c71c658846fdc1aed0aa81").unwrap().as_slice()).unwrap(),
    //     }]),
    //     SchnorrSighashType::All,
    // ).unwrap();
    //
    //
    //
    const MESSAGE: &'static [u8] = b"Hello, World!";
    let digest = &Sha256::digest(MESSAGE);
    let digest_u8: &[u8] = digest;
    let digest_i8 = unsafe { &*(digest_u8 as *const _ as *const [i8]) };
    let MESSAGe_DIGEST = digest_u8;
    let MESSAGE_DIGEST_i8 = digest_i8;

    let machine1 = SchnorrKeyGenWrapper::new(2, 2, 1, "test".into());
    let machine2 = SchnorrKeyGenWrapper::new(2, 2, 2, "test".into());

    let res1m1 = SchnorrKeyGenWrapper::key_gen_1_create_commitments(machine1);
    let machine1 = res1m1.keygen;
    let commitments_1 = res1m1.res;
    let res1m2 = SchnorrKeyGenWrapper::key_gen_1_create_commitments(machine2);
    let machine2 = res1m2.keygen;
    let commitments_2 = res1m2.res;

    let res2m1 = SchnorrKeyGenWrapper::key_gen_2_generate_shares(
        machine1,
        crate::ParamsKeygen2 {
            commitment_user_indices: vec![2],
            commitments: vec![commitments_2.clone()],
        },
    );
    let machine1 = res2m1.keygen;
    let res2m2 = SchnorrKeyGenWrapper::key_gen_2_generate_shares(
        machine2,
        crate::ParamsKeygen2 {
            commitment_user_indices: vec![1],
            commitments: vec![commitments_1.clone()],
        },
    );
    let machine2 = res2m2.keygen;

    let shares_for_1 = res2m2
        .shares
        .get(0)
        .unwrap()
        .iter()
        .map(|&x| x as i8)
        .collect();
    let res3m1 = SchnorrKeyGenWrapper::key_gen_3_complete(
        machine1,
        crate::ParamsKeygen3 {
            shares_user_indices: vec![2],
            shares: vec![shares_for_1],
        },
    );

    let shares_for_2 = res2m1
        .shares
        .get(0)
        .unwrap()
        .iter()
        .map(|&x| x as i8)
        .collect();
    let res3m2 = SchnorrKeyGenWrapper::key_gen_3_complete(
        machine2,
        crate::ParamsKeygen3 {
            shares_user_indices: vec![1],
            shares: vec![shares_for_2],
        },
    );

    let sign_wrapper_1 = SchnorrSignWrapper::new_instance_for_signing(&res3m1, 2);
    let sign_wrapper_2 = SchnorrSignWrapper::new_instance_for_signing(&res3m2, 2);

    let tweak = sign_wrapper_1.tweak.clone();

    let sign_res_1_m_1 = SchnorrSignWrapper::sign_1_preprocess(sign_wrapper_1);
    let sign_wrapper_1 = sign_res_1_m_1.wrapper;

    let sign_res_1_m_2 = SchnorrSignWrapper::sign_1_preprocess(sign_wrapper_2);
    let sign_wrapper_2 = sign_res_1_m_2.wrapper;

    // let hash_i8 =  unsafe{&*(hash.as_ref() as *const _  as *const [i8]) };
    let mut param_m1 = SignParams2 {
        commitments: HashMap::default(),
    };
    param_m1.add_commitment_from_user(
        2,
        &sign_res_1_m_2
            .preprocess
            .iter()
            .map(|&x| x as i8)
            .collect::<Vec<i8>>()[..],
    );
    let sign_res_2_m_1 =
        SchnorrSignWrapper::sign_2_sign_normal(sign_wrapper_1, param_m1, digest_i8);

    let mut param_m2 = SignParams2 {
        commitments: HashMap::default(),
    };
    param_m2.add_commitment_from_user(
        1,
        &sign_res_1_m_1
            .preprocess
            .iter()
            .map(|&x| x as i8)
            .collect::<Vec<i8>>()[..],
    );
    let sign_res_2_m_2 =
        SchnorrSignWrapper::sign_2_sign_normal(sign_wrapper_2, param_m2, digest_i8);

    let sign_wrapper_1 = sign_res_2_m_1.wrapper;

    let mut param_comlete = crate::SignParams3::new();
    param_comlete.add_share_of_user(
        2,
        &sign_res_2_m_2
            .share
            .iter()
            .map(|&x| x as i8)
            .collect::<Vec<i8>>()[..],
    );
    let sig = SchnorrSignWrapper::sign_3_complete(sign_wrapper_1, param_comlete);

    let sig_buf: Vec<u8> = sig.iter().map(|&x| x as u8).collect();
    let sig_obj = secp256k1::schnorr::Signature::from_slice(&sig_buf.as_ref()).unwrap();

    let mut verifykey = res3m1.key.clone();

    verifykey = verifykey.offset(Scalar::from(tweak));
    // println!("offset: {:?}",res3m1.key.current_offset());
    let pubkey_compressed = verifykey.group_key().to_encoded_point(true);
    let pubkey =
        secp256k1::XOnlyPublicKey::from_slice(&pubkey_compressed.x().to_owned().unwrap()).unwrap();

    let msg = Message::from_slice(digest_u8).unwrap();

    //would error if not valid
    let _res = sig_obj.verify(&msg, &pubkey).unwrap();
    println!("veirfy res : {:?}", _res);
    // let challenge = BitcoinHram::hram(&sigObj.R, &res3m1.key.group_key(), &[1]);

    // let verify = sigObj.verify(res3m1.key.group_key(), challenge);

    // let (machine1,commitments_1) = machine1.key_gen_1_create_commitments();
    // let (machine2,commitments_2) = machine2.key_gen_1_create_commitments();

    // let commitments_for_1 = vec![(2,commitments_2.clone())];
    // let commitments_for_2 = vec![(1,commitments_1.clone())];

    // let (machine1, shares1) = machine1.key_gen_2_generate_shares(commitments_for_1);
    // let (machine2, shares2) = machine2.key_gen_2_generate_shares(commitments_for_2);

    // println!("size: {:?}",shares2.len());

    // let shares_for_1 = vec![(2,shares2.into_iter().find(|x| true).unwrap().1)];
    // let shares_for_2 = vec![(1,shares1.into_iter().find(|x| true).unwrap().1)];

    // println!("share for 1 : {:?}",shares_for_1);

    // let sig_wrapper1 = machine1.key_gen_3_complete(shares_for_1);
    // let sig_wrapper2 = machine2.key_gen_3_complete(shares_for_2);

    // let (machine1,commitments_1) = machine1.key_gen_1_create_commitments();
}
